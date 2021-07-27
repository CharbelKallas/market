package com.market.service;

import com.market.exception.BRSException;
import com.market.exception.EntityType;
import com.market.exception.ExceptionType;
import com.market.model.user.User;
import com.market.model.user.UserOtp;
import com.market.payload.request.LoginRequest;
import com.market.payload.request.UserDto;
import com.market.payload.response.JwtResponse;
import com.market.repository.user.UserRepository;
import com.market.security.jwt.JwtUtils;
import com.market.security.services.UserDetailsImpl;
import com.market.util.RandomStringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Date;

import static com.market.exception.EntityType.USER;
import static com.market.exception.ExceptionType.DUPLICATE_ENTITY;

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
    private EmailService emailService;

    @Value("${app.otpExpirationMs}")
    private int otpExpirationMs;

//    @Autowired
//    private ModelMapper modelMapper;

    @Override
    public UserDto signup(UserDto userDto) {

        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw exception(USER, DUPLICATE_ENTITY, userDto.getUsername());
        }

        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw exception(USER, DUPLICATE_ENTITY, userDto.getEmail());
        }


        User user = new User()
                .setUsername(userDto.getUsername())
                .setEmail(userDto.getEmail())
                .setPassword(passwordEncoder.encode(userDto.getPassword()))
                .setFirstName(userDto.getFirstName())
                .setLastName(userDto.getLastName())
                .setMobileNumber(userDto.getMobileNumber());

        UserOtp userOtp = new UserOtp()
                .setUser(user)
                .setExpiryDate(new Date((new Date()).getTime() + otpExpirationMs))
                .setOtp(RandomStringUtil.getAlphaNumericString(5, user.getUsername() + user.getPassword() + user.getCreatedOn()));

        emailService.sendSimpleMessage(user.getEmail(), "OTP verification", "Your OTP is : " + userOtp.getOtp());

        return toUserDto(userRepository.save(user));
    }

    @Override
    public JwtResponse signin(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        return new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail());
    }

//    @Override
//    public UserDto updateProfile(UserDto userDto) {
//        User user = userRepository.findOneByUsername(userDto.getUsername()).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, userDto.getEmail()));
//        user.setFirstName(userDto.getFirstName())
//                .setLastName(userDto.getLastName())
//                .setMobileNumber(userDto.getMobileNumber());
//        return toUserDto(userRepository.save(user));
//
//    }

//    @Override
//    public UserDto changePassword(UserDto userDto, String newPassword) {
//        User user = userRepository.findOneByUsername(userDto.getUsername()).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, userDto.getEmail()));
//        user.setPassword(passwordEncoder.encode(newPassword));
//        return toUserDto(userRepository.save(user));
//    }

    private RuntimeException exception(EntityType entityType, ExceptionType exceptionType, String... args) {
        return BRSException.throwException(entityType, exceptionType, args);
    }

    public UserDto toUserDto(User user) {
        return new UserDto()
                .setEmail(user.getEmail())
                .setUsername(user.getUsername())
                .setFirstName(user.getFirstName())
                .setLastName(user.getLastName())
                .setMobileNumber(user.getMobileNumber());
    }
}
