package com.market.service;

import com.market.payload.request.LoginRequest;
import com.market.payload.request.UserDto;
import com.market.payload.response.JwtResponse;

public interface UserService {
    UserDto signup(UserDto userDto);
    JwtResponse signin(LoginRequest loginRequest);
//    UserDto updateProfile(UserDto userDto);
//    UserDto changePassword(UserDto userDto, String newPassword);
}
