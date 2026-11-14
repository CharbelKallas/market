package com.market.service;

import com.market.payload.request.UserSignupRequest;
import com.market.payload.response.JwtResponse;
import com.market.payload.response.UserResponse;

public interface UserService {
    UserResponse signup(UserSignupRequest request);

    JwtResponse signin(String username, String password, String clientIp);

    /** Returns a new token: all earlier tokens, including the caller's, stop working. */
    JwtResponse changePassword(Long userId, String oldPassword, String newPassword);
}
