package com.market.service;

import com.market.payload.response.JwtResponse;
import com.market.payload.response.UserResponse;

public interface UserService {
    UserResponse signup(UserResponse userResponse);

    JwtResponse signin(String username, String password);

    Boolean verify(Long userId, String otp);

    void resendOtp(Long request);

    UserResponse updateProfile(UserResponse userResponse);

    void changePassword(Long userId, String oldPassword, String newPassword);
}
