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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    static final int MAX_OTP_ATTEMPTS = 5;

    static final long OTP_RESEND_COOLDOWN_MS = 60_000;

    static final int MAX_RESENDS_PER_DAY = 5;

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

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
        String username = User.normalizeIdentifier(request.getUsername());
        String email = User.normalizeIdentifier(request.getEmail());

        Optional<User> sameUsername = userRepository.findOneByUsername(username);
        if (sameUsername.isPresent())
            releaseIfAbandoned(sameUsername.get(), "Username " + username + " is already taken.");

        Optional<User> sameEmail = userRepository.findOneByEmail(email);
        if (sameEmail.isPresent())
            releaseIfAbandoned(sameEmail.get(), "Email " + email + " is already registered.");

        User user = new User()
                .setUsername(username)
                .setEmail(email)
                .setPassword(passwordEncoder.encode(request.getPassword()))
                .setFirstName(request.getFirstName())
                .setLastName(request.getLastName())
                .setMobileNumber(request.getMobileNumber());

        user.setUserRoles(new HashSet<>(Set.of(new UserRole().setName(Role.ROLE_USER).setUser(user))));

        UserOtp userOtp = newOtp(user);
        user.setUserOtps(new HashSet<>(Set.of(userOtp)));

        User saved = userRepository.save(user);
        sendOtpAfterCommit(saved, userOtp.getOtp());

        return toUserResponse(saved);
    }

    @Override
    public JwtResponse signin(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(User.normalizeIdentifier(username), password));

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
        if (otp.getResendWindowStart() == null || now.getTime() - otp.getResendWindowStart().getTime() >= DAY_MS)
            otp.setResendWindowStart(now).setResendCount(0);
        // Each resend resets the guess limit, so resends themselves must be limited.
        if (otp.getResendCount() >= MAX_RESENDS_PER_DAY)
            throw MarketException.badRequest("Too many OTP requests. Please try again tomorrow.");

        otp.setExpiryDate(new Date(now.getTime() + otpExpirationMs))
                .setSentDate(now)
                .setFailedAttempts(0)
                .setResendCount(otp.getResendCount() + 1)
                .setOtp(generateOtp());
        otpRepository.save(otp);

        sendOtpAfterCommit(user, otp.getOtp());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> MarketException.notFound("User - " + userId + " does not exist."));

        if (!passwordEncoder.matches(oldPassword, user.getPassword()))
            throw MarketException.badRequest("The old password is not valid.");

        user.setPassword(passwordEncoder.encode(newPassword))
                .setTokenVersion(user.getTokenVersion() + 1);
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

    /**
     * An unverified account whose OTP has expired is abandoned: delete it so the username or email can be used again.
     * Otherwise anyone could block an email forever by signing up with it and never verifying.
     */
    private void releaseIfAbandoned(User existing, String conflictMessage) {
        Date now = new Date();
        boolean pendingOtp = existing.getUserOtps() != null && existing.getUserOtps().stream()
                .anyMatch(otp -> otp.getExpiryDate().after(now));
        if (existing.getVerifiedDate() != null || pendingOtp)
            throw MarketException.duplicate(conflictMessage);

        userRepository.delete(existing);
        userRepository.flush();
    }

    /**
     * Sends the OTP only once the transaction has committed, so no DB connection is held during the SMTP/SMS call
     * and nobody receives a code for an account that was rolled back. The data is already saved by then, so a send
     * failure is logged rather than turned into an error response; the user can request a new code.
     */
    private void sendOtpAfterCommit(User user, String otp) {
        String email = user.getEmail();
        String mobileNumber = user.getMobileNumber();
        Long userId = user.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messageService.sendEmailMessage(email, "OTP verification", "Your OTP is : " + otp);
                } catch (RuntimeException e) {
                    log.error("Could not send the OTP email to user {}", userId, e);
                }
                try {
                    messageService.sendSmsMessage(mobileNumber, "Your OTP is : " + otp);
                } catch (RuntimeException e) {
                    log.error("Could not send the OTP SMS to user {}", userId, e);
                }
            }
        });
    }

}
