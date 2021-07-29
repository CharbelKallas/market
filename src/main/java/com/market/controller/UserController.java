package com.market.controller;

import com.market.payload.request.*;
import com.market.payload.response.Response;
import com.market.payload.response.UserDto;
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

    @Autowired
    private UserService userService;

    @PostMapping("/signup")
    public Response<?> signup(@RequestBody @Valid UserSignupRequest request) {
        UserDto userDto = new UserDto()
                .setEmail(request.getEmail())
                .setUsername(request.getUsername())
                .setPassword(request.getPassword())
                .setFirstName(request.getFirstName())
                .setLastName(request.getLastName())
                .setMobileNumber(request.getMobileNumber());

        return Response.ok().setPayload(userService.signup(userDto));
    }

    @PostMapping("/signin")
    public Response<?> signin(@RequestBody @Valid LoginRequest request) {
        return Response.ok().setPayload(userService.signin(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/verify")
    public Response<?> verify(@RequestBody @Valid VerifyRequest request) {
        Boolean verified = userService.verify(request.getUserId(), request.getOtp());
        if (verified)
            return Response.ok().setPayload("Activated");
        else
            return Response.wrongCredentials().setPayload("Wrong Credentials or OTP Expired");
    }

    @PostMapping("/resend_otp")
    public Response<?> resendOtp(@RequestBody @Valid ResendOtpRequest request) {
        userService.resendOtp(request.getUserId());
        return Response.ok().setPayload("Done");
    }

    @PostMapping("/change_password")
//    @PreAuthorize("hasRole('USER')")
    public Response<?> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(request.getUserId(), request.getOldPassword(), request.getNewPassword());
        return Response.ok().setPayload("Done");
    }

}
