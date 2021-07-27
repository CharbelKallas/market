package com.market.controller;

import com.market.payload.request.*;
import com.market.payload.response.Response;
import com.market.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public Response<?> signup(@RequestBody @Valid UserSignupRequest userSignupRequest) {
        return Response.ok().setPayload(registerUser(userSignupRequest));
    }

    @PostMapping("/signin")
    public Response<?> signin(@RequestBody @Valid LoginRequest loginRequest) {
        return Response.ok().setPayload(userService.signin(loginRequest));
    }

    @PostMapping("/verify")
    public Response<?> verify(@RequestBody @Valid VerifyRequest verifyRequest) {
        Boolean verified = userService.verify(verifyRequest);
        if (verified)
            return Response.ok().setPayload("Activated");
        else
            return Response.wrongCredentials().setPayload("Wrong Credentials or OTP Expired");
    }

    @PostMapping("/resend_otp")
    public Response<?> resendOtp(@RequestBody @Valid ResendOtpRequest request) {
        userService.resendOtp(request);
        return Response.ok().setPayload("Done");
    }

    private UserDto registerUser(UserSignupRequest userSignupRequest) {
        UserDto userDto = new UserDto()
                .setEmail(userSignupRequest.getEmail())
                .setUsername(userSignupRequest.getUsername())
                .setPassword(userSignupRequest.getPassword())
                .setFirstName(userSignupRequest.getFirstName())
                .setLastName(userSignupRequest.getLastName())
                .setMobileNumber(userSignupRequest.getMobileNumber());

        return userService.signup(userDto);
    }
}
