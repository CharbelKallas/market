package com.market.service;

import com.market.payload.response.UserDto;
import com.market.payload.response.JwtResponse;

public interface UserService {
    UserDto signup(UserDto userDto);

    JwtResponse signin(String username, String password);

    Boolean verify(Long userId, String otp);

    void resendOtp(Long request);

    UserDto updateProfile(UserDto userDto);

    void changePassword(Long userId, String oldPassword, String newPassword);
}
