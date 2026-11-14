package com.market.service;

import com.market.payload.request.UserSignupRequest;
import com.market.payload.response.JwtResponse;
import com.market.payload.response.UserResponse;

public interface UserService {
    UserResponse signup(UserSignupRequest request);

    JwtResponse signin(String username, String password);

    boolean verify(Long userId, String otp);

    void resendOtp(Long userId);

    void changePassword(Long userId, String oldPassword, String newPassword);
}
