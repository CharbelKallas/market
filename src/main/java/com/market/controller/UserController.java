package com.market.controller;

import com.market.exception.MarketException;
import com.market.payload.Response;
import com.market.payload.request.*;
import com.market.payload.response.JwtResponse;
import com.market.payload.response.UserResponse;
import com.market.security.services.UserDetailsImpl;
import com.market.service.OtpService;
import com.market.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    private final OtpService otpService;

    public UserController(UserService userService, OtpService otpService) {
        this.userService = userService;
        this.otpService = otpService;
    }

    @PostMapping("/signup")
    public Response<UserResponse> signup(@RequestBody @Valid UserSignupRequest request) {
        return Response.<UserResponse>ok().setPayload(userService.signup(request));
    }

    @PostMapping("/signin")
    public Response<JwtResponse> signin(@RequestBody @Valid LoginRequest request, HttpServletRequest httpRequest) {
        return Response.<JwtResponse>ok().setPayload(
                userService.signin(request.getUsername(), request.getPassword(), httpRequest.getRemoteAddr()));
    }

    @PostMapping("/verify")
    public Response<String> verify(@RequestBody @Valid VerifyRequest request) {
        if (!otpService.verify(request.getUserId(), request.getOtp()))
            throw MarketException.badRequest("Wrong OTP or OTP expired.");
        return Response.<String>ok().setPayload("Activated");
    }

    @PostMapping("/resend_otp")
    public Response<String> resendOtp(@RequestBody @Valid ResendOtpRequest request) {
        otpService.resend(request.getUserId());
        return Response.<String>ok().setPayload("Done");
    }

    /** Changes the signed-in user's password and returns a new token (older tokens stop working). */
    @PostMapping("/change_password")
    @PreAuthorize("isAuthenticated()")
    public Response<JwtResponse> changePassword(@AuthenticationPrincipal UserDetailsImpl user,
                                                @RequestBody @Valid ChangePasswordRequest request) {
        return Response.<JwtResponse>ok()
                .setPayload(userService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword()));
    }
}
