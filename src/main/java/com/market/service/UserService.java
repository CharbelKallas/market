package com.market.service;

import com.market.payload.request.UserDto;

import java.util.List;

public interface UserService {
    UserDto signup(UserDto userDto);

    UserDto findUserByEmail(String email);

    UserDto updateProfile(UserDto userDto);

    UserDto changePassword(UserDto userDto, String newPassword);

    List<UserDto> findAll();
}
