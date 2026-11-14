package com.market.payload.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import com.market.payload.validation.BcryptPassword;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangePasswordRequest {
    @NotBlank
    private String oldPassword;

    @NotBlank
    @BcryptPassword
    private String newPassword;
}
