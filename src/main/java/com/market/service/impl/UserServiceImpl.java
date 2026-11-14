package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.model.user.Role;
import com.market.model.user.User;
import com.market.model.user.UserOtp;
import com.market.model.user.UserRole;
import com.market.payload.request.UserSignupRequest;
import com.market.payload.response.JwtResponse;
import com.market.payload.response.UserResponse;
import com.market.repository.UserRepository;
import com.market.security.jwt.JwtUtils;
import com.market.security.services.UserDetailsImpl;
import com.market.service.LoginAttemptService;
import com.market.service.OtpService;
import com.market.service.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Accounts: sign-up, sign-in and password changes. Codes are handled by {@link OtpService}. */
@Service
public class UserServiceImpl implements UserService {

    private final AuthenticationManager authenticationManager;

    private final JwtUtils jwtUtils;

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    private final OtpService otpService;

    private final LoginAttemptService loginAttempts;

    public UserServiceImpl(AuthenticationManager authenticationManager, JwtUtils jwtUtils, PasswordEncoder passwordEncoder,
                           UserRepository userRepository, OtpService otpService, LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.loginAttempts = loginAttempts;
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

        UserOtp userOtp = otpService.newOtp(user);
        user.setUserOtps(new HashSet<>(Set.of(userOtp)));

        User saved = userRepository.save(user);
        otpService.sendAfterCommit(saved, userOtp.getOtp());

        return toUserResponse(saved);
    }

    @Override
    public JwtResponse signin(String username, String password, String clientIp) {
        String normalized = User.normalizeIdentifier(username);
        loginAttempts.checkNotBlocked(normalized, clientIp);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalized, password));
        } catch (BadCredentialsException e) {
            loginAttempts.recordFailure(normalized, clientIp);
            throw e;
        }
        loginAttempts.recordSuccess(normalized, clientIp);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        // Checked only after the password matched, so it doesn't reveal which usernames exist.
        if (!userDetails.isVerified())
            throw new DisabledException("Account is not verified yet");

        return toJwtResponse(userDetails);
    }

    private JwtResponse toJwtResponse(UserDetailsImpl userDetails) {
        return new JwtResponse(jwtUtils.generateJwtToken(userDetails),
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail());
    }

    @Override
    public boolean verify(Long userId, String otp) {
        return otpService.verify(userId, otp);
    }

    @Override
    public void resendOtp(Long userId) {
        otpService.resend(userId);
    }

    @Override
    @Transactional
    public JwtResponse changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> MarketException.notFound("User - " + userId + " does not exist."));

        if (!passwordEncoder.matches(oldPassword, user.getPassword()))
            throw MarketException.badRequest("The old password is not valid.");

        user.setPassword(passwordEncoder.encode(newPassword))
                .setTokenVersion(user.getTokenVersion() + 1);
        // Every older token (including the caller's) is now invalid, so hand back a fresh one.
        return toJwtResponse(UserDetailsImpl.build(user));
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
        if (existing.getVerifiedDate() != null || otpService.hasPendingOtp(existing))
            throw MarketException.duplicate(conflictMessage);

        userRepository.delete(existing);
        userRepository.flush();
    }

}
