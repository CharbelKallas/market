package com.market.service;

import com.market.exception.BRSException;
import com.market.exception.EntityType;
import com.market.exception.ExceptionType;
import com.market.model.user.User;
import com.market.payload.request.UserDto;
import com.market.repository.user.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.market.exception.EntityType.USER;
import static com.market.exception.ExceptionType.DUPLICATE_ENTITY;
import static com.market.exception.ExceptionType.ENTITY_NOT_FOUND;

@Component
public class UserServiceImpl implements UserService {

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public UserDto signup(UserDto userDto) {
        userRepository.findOneByUsername(userDto.getUsername()).ifPresent(usr -> {
            throw exception(USER, DUPLICATE_ENTITY, userDto.getEmail());
        });
        User user = new User()
                .setEmail(userDto.getEmail())
                .setPassword(bCryptPasswordEncoder.encode(userDto.getPassword()))
                .setFirstName(userDto.getFirstName())
                .setLastName(userDto.getLastName())
                .setMobileNumber(userDto.getMobileNumber());
        return toUserDto(userRepository.save(user));

    }

    @Transactional
    public UserDto findUserByEmail(String email) {
        User user = userRepository.findOneByEmail(email).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, email));
        return modelMapper.map(user, UserDto.class);
    }

    @Override
    public UserDto updateProfile(UserDto userDto) {
        User user = userRepository.findOneByUsername(userDto.getUsername()).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, userDto.getEmail()));
        user.setFirstName(userDto.getFirstName())
                .setLastName(userDto.getLastName())
                .setMobileNumber(userDto.getMobileNumber());
        return toUserDto(userRepository.save(user));

    }

    @Override
    public UserDto changePassword(UserDto userDto, String newPassword) {
        User user = userRepository.findOneByUsername(userDto.getUsername()).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, userDto.getEmail()));
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        return toUserDto(userRepository.save(user));
    }

    @Override
    public List<UserDto> findAll() {
        List<UserDto> userDtos = new ArrayList<>();
        userRepository.findAll().forEach(user -> userDtos.add(toUserDto(user)));
        return userDtos;
    }

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
