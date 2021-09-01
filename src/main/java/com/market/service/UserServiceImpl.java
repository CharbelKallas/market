package com.market.service;

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
import com.market.util.OtpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;

import static com.market.exception.EntityType.*;
import static com.market.exception.ExceptionType.DUPLICATE_ENTITY;
import static com.market.exception.ExceptionType.ENTITY_NOT_FOUND;

@Component
public class UserServiceImpl implements UserService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOtpRepository otpRepository;

    @Autowired
    private MessageService messageService;

    @Value("${app.otpExpirationMs}")
    private int OTP_EXPIRATION_MS;

    @Override
    public UserResponse signup(UserResponse userResponse) {

        if (userRepository.existsByUsername(userResponse.getUsername()))
            throw MarketException.throwException(USER, DUPLICATE_ENTITY, userResponse.getUsername());

        if (userRepository.existsByEmail(userResponse.getEmail()))
            throw MarketException.throwException(USER, DUPLICATE_ENTITY, userResponse.getEmail());

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
                .setExpiryDate(new Date((new Date()).getTime() + OTP_EXPIRATION_MS))
                .setOtp(OtpUtil.generateOTP());

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
                MarketException.throwException(OTP, ENTITY_NOT_FOUND));

        otp.setUser(userRepository.getById(request))
                .setExpiryDate(new Date((new Date()).getTime() + OTP_EXPIRATION_MS))
                .setOtp(OtpUtil.generateOTP());

        sendOtp(otp.getUser().getEmail(), otp.getUser().getMobileNumber(), otp.getOtp());

        otpRepository.save(otp);
    }

    @Override
    public UserResponse updateProfile(UserResponse userResponse) {
        User user = userRepository.findOneByUsername(userResponse.getUsername()).orElseThrow(() -> MarketException.throwException(USER, ENTITY_NOT_FOUND, userResponse.getUsername()));
        user.setFirstName(userResponse.getFirstName())
                .setLastName(userResponse.getLastName())
                .setMobileNumber(userResponse.getMobileNumber());
        return toUserResponse(userRepository.save(user));
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> MarketException.throwException(USER, ENTITY_NOT_FOUND, String.valueOf(userId)));

        if (!passwordEncoder.matches(oldPassword, user.getPassword()))
            throw MarketException.throwException(PASSWORD, ENTITY_NOT_FOUND, oldPassword);

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
