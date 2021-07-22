package com.market.service;

import com.market.dto.model.user.UserDto;

public interface UserService {
    UserDto signup(UserDto userDto);

    UserDto findUserByEmail(String email);

    UserDto updateProfile(UserDto userDto);

    UserDto changePassword(UserDto userDto, String newPassword);
}
