package com.market.service;

import com.market.dto.mapper.UserMapper;
import com.market.dto.model.user.UserDto;
import com.market.exception.BRSException;
import com.market.exception.EntityType;
import com.market.exception.ExceptionType;
import com.market.model.user.Role;
import com.market.model.user.User;
import com.market.model.user.UserRoles;
import com.market.repository.user.RoleRepository;
import com.market.repository.user.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;

import static com.market.exception.EntityType.USER;
import static com.market.exception.ExceptionType.DUPLICATE_ENTITY;
import static com.market.exception.ExceptionType.ENTITY_NOT_FOUND;

@Component
public class UserServiceImpl implements UserService {

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public UserDto signup(UserDto userDto) {
        Role userRole;
        User user = userRepository.findOneByEmail(userDto.getEmail()).orElseThrow(() -> exception(USER, DUPLICATE_ENTITY, userDto.getEmail()));
        if (userDto.isAdmin()) {
            userRole = roleRepository.findOneByRole(UserRoles.ADMIN).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, UserRoles.ADMIN.name()));
        } else {
            userRole = roleRepository.findOneByRole(UserRoles.USER).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, UserRoles.USER.name()));
        }
        user = new User()
                .setEmail(userDto.getEmail())
                .setPassword(bCryptPasswordEncoder.encode(userDto.getPassword()))
                .setRoles(new HashSet<>(Arrays.asList(userRole)))
                .setFirstName(userDto.getFirstName())
                .setLastName(userDto.getLastName())
                .setMobileNumber(userDto.getMobileNumber());
        return UserMapper.toUserDto(userRepository.save(user));
    }

    @Transactional
    public UserDto findUserByEmail(String email) {
        User user = userRepository.findOneByEmail(email).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, email));
        return modelMapper.map(user, UserDto.class);
    }

    @Override
    public UserDto updateProfile(UserDto userDto) {
        User user = userRepository.findOneByEmail(userDto.getEmail()).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, userDto.getEmail()));
        user.setFirstName(userDto.getFirstName())
                .setLastName(userDto.getLastName())
                .setMobileNumber(userDto.getMobileNumber());
        return UserMapper.toUserDto(userRepository.save(user));

    }

    @Override
    public UserDto changePassword(UserDto userDto, String newPassword) {
        User user = userRepository.findOneByEmail(userDto.getEmail()).orElseThrow(() -> exception(USER, ENTITY_NOT_FOUND, userDto.getEmail()));
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        return UserMapper.toUserDto(userRepository.save(user));
    }

    private RuntimeException exception(EntityType entityType, ExceptionType exceptionType, String... args) {
        return BRSException.throwException(entityType, exceptionType, args);
    }
}
