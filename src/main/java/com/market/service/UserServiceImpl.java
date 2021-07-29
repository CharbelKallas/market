package com.market.service;

import com.market.exception.BRSException;
import com.market.model.user.User;
import com.market.model.user.UserOtp;
import com.market.payload.response.JwtResponse;
import com.market.payload.response.UserDto;
import com.market.repository.user.UserOtpRepository;
import com.market.repository.user.UserRepository;
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
    public UserDto signup(UserDto userDto) {

        User user = userRepository.findOneByUsername(userDto.getUsername()).orElse(
                userRepository.findOneByUsername(userDto.getEmail()).orElse(new User()));

        if (user.getVerifiedDate() != null)
            throw BRSException.throwException(USER, DUPLICATE_ENTITY, userDto.getUsername(), userDto.getEmail());

        user.setUsername(userDto.getUsername())
                .setEmail(userDto.getEmail())
                .setPassword(passwordEncoder.encode(userDto.getPassword()))
                .setFirstName(userDto.getFirstName())
                .setLastName(userDto.getLastName())
                .setMobileNumber(userDto.getMobileNumber());

        UserOtp userOtp = otpRepository.findOneByUserId(user.getId()).orElse(new UserOtp());

        userOtp.setUser(user)
                .setExpiryDate(new Date((new Date()).getTime() + OTP_EXPIRATION_MS))
                .setOtp(OtpUtil.generateOTP());

        user.setUserOtps(new HashSet<>(Collections.singletonList(userOtp)));

        sendOtp(user.getEmail(), user.getMobileNumber(), userOtp.getOtp());

        return toUserDto(userRepository.save(user));
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
                BRSException.throwException(OTP, ENTITY_NOT_FOUND));

        otp.setUser(userRepository.getById(request))
                .setExpiryDate(new Date((new Date()).getTime() + OTP_EXPIRATION_MS))
                .setOtp(OtpUtil.generateOTP());

        sendOtp(otp.getUser().getEmail(), otp.getUser().getMobileNumber(), otp.getOtp());

        otpRepository.save(otp);
    }

    @Override
    public UserDto updateProfile(UserDto userDto) {
        User user = userRepository.findOneByUsername(userDto.getUsername()).orElseThrow(() -> BRSException.throwException(USER, ENTITY_NOT_FOUND, userDto.getUsername()));
        user.setFirstName(userDto.getFirstName())
                .setLastName(userDto.getLastName())
                .setMobileNumber(userDto.getMobileNumber());
        return toUserDto(userRepository.save(user));
    }

    @Override
    public UserDto changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> BRSException.throwException(USER, ENTITY_NOT_FOUND, String.valueOf(userId)));

        if (!passwordEncoder.matches(oldPassword, user.getPassword()))
            throw BRSException.throwException(PASSWORD, ENTITY_NOT_FOUND, oldPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        return toUserDto(userRepository.save(user));
    }

    public UserDto toUserDto(User user) {
        return new UserDto()
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
