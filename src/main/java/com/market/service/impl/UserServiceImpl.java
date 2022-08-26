package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.model.user.Role;
import com.market.model.user.User;
import com.market.model.user.UserOtp;
import com.market.model.user.UserRole;
import com.market.payload.response.JwtResponse;
import com.market.payload.response.UserResponse;
import com.market.repository.UserOtpRepository;
import com.market.repository.UserRepository;
import com.market.security.jwt.JwtUtils;
import com.market.security.services.UserDetailsImpl;
import com.market.service.MessageService;
import com.market.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final AuthenticationManager authenticationManager;

    private final JwtUtils jwtUtils;

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    private final UserOtpRepository otpRepository;

    private final MessageService messageService;

    @Value("${app.otpExpirationMs}")
    private int otpExpirationMs;
    @Value("${otp.size}")
    private double otpSize;

    @Autowired
    public UserServiceImpl(AuthenticationManager authenticationManager, JwtUtils jwtUtils, PasswordEncoder passwordEncoder, UserRepository userRepository, UserOtpRepository otpRepository, MessageService messageService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.messageService = messageService;
    }

    @Override
    public UserResponse signup(UserResponse userResponse) {

        if (userRepository.existsByUsername(userResponse.getUsername()))
            throw MarketException.throwException("User - " + userResponse.getUsername() + " already exists.");

        if (userRepository.existsByEmail(userResponse.getEmail()))
            throw MarketException.throwException("User - " + userResponse.getEmail() + " already exists.");

        User user = new User()
                .setUsername(userResponse.getUsername())
                .setEmail(userResponse.getEmail())
                .setPassword(passwordEncoder.encode(userResponse.getPassword()))
                .setFirstName(userResponse.getFirstName())
                .setLastName(userResponse.getLastName())
                .setMobileNumber(userResponse.getMobileNumber());

        user.setUserRoles(Collections.singleton(new UserRole().setName(Role.ROLE_USER).setUser(user)));

        UserOtp userOtp = new UserOtp()
                .setUser(user)
                .setExpiryDate(new Date((new Date()).getTime() + otpExpirationMs))
                .setOtp(generateOTP());

        user.setUserOtps(new HashSet<>(Collections.singletonList(userOtp)));

        sendOtp(user.getEmail(), user.getMobileNumber(), userOtp.getOtp());

        return toUserResponse(userRepository.save(user));
    }

    @Override
    public JwtResponse signin(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        return new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail());
    }

    @Override
    public Boolean verify(Long userId, String otp) {
        Optional<UserOtp> userOtp = otpRepository.findOneByUserIdAndOtpAndExpiryDateGreaterThan(userId, otp, new Date());

        if (userOtp.isPresent()) {
            userRepository.save(userRepository.getById(userId).setVerifiedDate(new Date()));
            otpRepository.delete(userOtp.get());
        }
        return userOtp.isPresent();
    }

    @Override
    public void resendOtp(Long request) {

        UserOtp otp = otpRepository.findOneByUserId(request).orElseThrow(() ->
                MarketException.throwException("OTP for User does not exist."));

        otp.setUser(userRepository.getById(request))
                .setExpiryDate(new Date((new Date()).getTime() + otpExpirationMs))
                .setOtp(generateOTP());

        sendOtp(otp.getUser().getEmail(), otp.getUser().getMobileNumber(), otp.getOtp());

        otpRepository.save(otp);
    }

    private String generateOTP() {
        int otp = (int) ((Math.random() * 9 * Math.pow(10, otpSize - 1)) + (Math.pow(10, otpSize - 1)));
        return String.valueOf(otp);
    }

    @Override
    public UserResponse updateProfile(UserResponse userResponse) {
        User user = userRepository.findOneByUsername(userResponse.getUsername()).orElseThrow(() -> MarketException.throwException("User - " + userResponse.getUsername() + " does not exist."));
        user.setFirstName(userResponse.getFirstName())
                .setLastName(userResponse.getLastName())
                .setMobileNumber(userResponse.getMobileNumber());
        return toUserResponse(userRepository.save(user));
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> MarketException.throwException("User - " + userId + " does not exist."));

        if (!passwordEncoder.matches(oldPassword, user.getPassword()))
            throw MarketException.throwException("The Old Password is not valid");

        user.setPassword(passwordEncoder.encode(newPassword));
        toUserResponse(userRepository.save(user));
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse()
                .setId(user.getId())
                .setEmail(user.getEmail())
                .setUsername(user.getUsername())
                .setFirstName(user.getFirstName())
                .setLastName(user.getLastName())
                .setMobileNumber(user.getMobileNumber());
    }

    private void sendOtp(String email, String mobileNumber, String otp) {
        messageService.sendEmailMessage(email, "OTP verification", "Your OTP is : " + otp);
        messageService.sendSmsMessage(mobileNumber, "Your OTP is : " + otp);
    }

}
