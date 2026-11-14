package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.model.user.Role;
import com.market.model.user.User;
import com.market.model.user.UserOtp;
import com.market.model.user.UserRole;
import com.market.payload.request.UserSignupRequest;
import com.market.payload.response.JwtResponse;
import com.market.payload.response.UserResponse;
import com.market.repository.UserOtpRepository;
import com.market.repository.UserRepository;
import com.market.security.jwt.JwtUtils;
import com.market.security.services.UserDetailsImpl;
import com.market.service.MessageService;
import com.market.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    static final int MAX_OTP_ATTEMPTS = 5;

    static final long OTP_RESEND_COOLDOWN_MS = 60_000;

    private final AuthenticationManager authenticationManager;

    private final JwtUtils jwtUtils;

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    private final UserOtpRepository otpRepository;

    private final MessageService messageService;

    private final long otpExpirationMs;

    private final int otpSize;

    public UserServiceImpl(AuthenticationManager authenticationManager, JwtUtils jwtUtils, PasswordEncoder passwordEncoder,
                           UserRepository userRepository, UserOtpRepository otpRepository, MessageService messageService,
                           @Value("${app.otpExpirationMs}") long otpExpirationMs,
                           @Value("${otp.size}") int otpSize) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.messageService = messageService;
        this.otpExpirationMs = otpExpirationMs;
        this.otpSize = otpSize;
    }

    @Override
    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw MarketException.duplicate("Username " + request.getUsername() + " is already taken.");

        if (userRepository.existsByEmail(request.getEmail()))
            throw MarketException.duplicate("Email " + request.getEmail() + " is already registered.");

        User user = new User()
                .setUsername(request.getUsername())
                .setEmail(request.getEmail())
                .setPassword(passwordEncoder.encode(request.getPassword()))
                .setFirstName(request.getFirstName())
                .setLastName(request.getLastName())
                .setMobileNumber(request.getMobileNumber());

        user.setUserRoles(new HashSet<>(Set.of(new UserRole().setName(Role.ROLE_USER).setUser(user))));

        UserOtp userOtp = newOtp(user);
        user.setUserOtps(new HashSet<>(Set.of(userOtp)));

        User saved = userRepository.save(user);
        // Sent last: if sending fails, the transaction rolls back and no half-created user is left behind.
        sendOtp(saved, userOtp.getOtp());

        return toUserResponse(saved);
    }

    @Override
    public JwtResponse signin(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        // Checked only after the password matched, so it doesn't reveal which usernames exist.
        if (!userDetails.isVerified())
            throw new DisabledException("Account is not verified yet");

        String jwt = jwtUtils.generateJwtToken(authentication);

        return new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail());
    }

    @Override
    @Transactional
    public boolean verify(Long userId, String otp) {
        Optional<UserOtp> found = otpRepository.findOneByUserId(userId);
        if (found.isEmpty())
            return false;

        UserOtp userOtp = found.get();
        User user = userOtp.getUser();
        // A locked-out OTP is kept (not deleted) so its sent date still enforces the resend cooldown.
        if (userOtp.getExpiryDate().before(new Date()) || userOtp.getFailedAttempts() >= MAX_OTP_ATTEMPTS)
            return false;

        if (!MessageDigest.isEqual(userOtp.getOtp().getBytes(StandardCharsets.UTF_8), otp.getBytes(StandardCharsets.UTF_8))) {
            // Returning (not throwing) keeps the transaction, so the failed attempt is saved.
            userOtp.setFailedAttempts(userOtp.getFailedAttempts() + 1);
            return false;
        }

        user.setVerifiedDate(new Date());
        user.getUserOtps().remove(userOtp);
        otpRepository.delete(userOtp);
        return true;
    }

    @Override
    @Transactional
    public void resendOtp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> MarketException.notFound("User - " + userId + " does not exist."));

        if (user.getVerifiedDate() != null)
            throw MarketException.badRequest("User is already verified.");

        UserOtp otp = otpRepository.findOneByUserId(userId).orElse(null);
        if (otp != null && otp.getSentDate() != null
                && System.currentTimeMillis() - otp.getSentDate().getTime() < OTP_RESEND_COOLDOWN_MS)
            throw MarketException.badRequest("Please wait a minute before requesting a new OTP.");

        if (otp == null)
            otp = newOtp(user);
        Date now = new Date();
        otp.setExpiryDate(new Date(now.getTime() + otpExpirationMs))
                .setSentDate(now)
                .setFailedAttempts(0)
                .setOtp(generateOtp());
        otpRepository.save(otp);

        sendOtp(user, otp.getOtp());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> MarketException.notFound("User - " + userId + " does not exist."));

        if (!passwordEncoder.matches(oldPassword, user.getPassword()))
            throw MarketException.badRequest("The old password is not valid.");

        user.setPassword(passwordEncoder.encode(newPassword));
    }

    private UserOtp newOtp(User user) {
        Date now = new Date();
        return new UserOtp()
                .setUser(user)
                .setSentDate(now)
                .setExpiryDate(new Date(now.getTime() + otpExpirationMs))
                .setOtp(generateOtp());
    }

    private String generateOtp() {
        int lowerBound = (int) Math.pow(10, otpSize - 1);
        return String.valueOf(lowerBound + RANDOM.nextInt(9 * lowerBound));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse()
                .setId(user.getId())
                .setEmail(user.getEmail())
                .setUsername(user.getUsername())
                .setFirstName(user.getFirstName())
                .setLastName(user.getLastName())
                .setMobileNumber(user.getMobileNumber());
    }

    private void sendOtp(User user, String otp) {
        messageService.sendEmailMessage(user.getEmail(), "OTP verification", "Your OTP is : " + otp);
        messageService.sendSmsMessage(user.getMobileNumber(), "Your OTP is : " + otp);
    }

}
