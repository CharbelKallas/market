package com.market.controller;

import com.market.payload.request.LoginRequest;
import com.market.payload.request.UserDto;
import com.market.payload.request.UserSignupRequest;
import com.market.payload.request.VerifyRequest;
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
            return Response.wrongCredentials().setPayload("Not Activated");
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
