package com.market.payload.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.market.payload.validation.BcryptPassword;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSignupRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
            message = "must be 3-50 characters: letters, digits, '.', '_' or '-'")
    private String username;

    @NotBlank
    @BcryptPassword
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    /** International (E.164) format, e.g. +96170123456, so SMS sending can actually reach it. */
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be in international format, e.g. +96170123456")
    private String mobileNumber;
}
