package com.market.controller;

import com.market.payload.Response;
import com.market.payload.request.*;
import com.market.payload.response.UserResponse;
import com.market.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public Response<Object> signup(@RequestBody @Valid UserSignupRequest request) {
        UserResponse userResponse = new UserResponse()
                .setEmail(request.getEmail())
                .setUsername(request.getUsername())
                .setPassword(request.getPassword())
                .setFirstName(request.getFirstName())
                .setLastName(request.getLastName())
                .setMobileNumber(request.getMobileNumber());

        return Response.ok().setPayload(userService.signup(userResponse));
    }

    @PostMapping("/signin")
    public Response<Object> signin(@RequestBody @Valid LoginRequest request) {
        return Response.ok().setPayload(userService.signin(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/verify")
    public Response<Object> verify(@RequestBody @Valid VerifyRequest request) {
        Boolean verified = userService.verify(request.getUserId(), request.getOtp());
        if (verified)
            return Response.ok().setPayload("Activated");
        else
            return Response.wrongCredentials().setPayload("Wrong Credentials or OTP Expired");
    }

    @PostMapping("/resend_otp")
    public Response<Object> resendOtp(@RequestBody @Valid ResendOtpRequest request) {
        userService.resendOtp(request.getUserId());
        return Response.ok().setPayload("Done");
    }

    @PostMapping("/change_password")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public Response<Object> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(request.getUserId(), request.getOldPassword(), request.getNewPassword());
        return Response.ok().setPayload("Done");
    }
}
