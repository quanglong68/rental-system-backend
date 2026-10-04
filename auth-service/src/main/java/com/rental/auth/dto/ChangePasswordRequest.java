package com.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request doi mat khau (docs Table 7): sai mat khau cu -> 400 WRONG_OLD_PASSWORD.
 * Doi mat khau se thu hoi toan bo refresh token (mục 7.1).
 */
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Khong duoc de trong")
    private String oldPassword;

    @NotBlank(message = "Khong duoc de trong")
    @Size(min = 8, message = "Mat khau moi toi thieu 8 ky tu")
    private String newPassword;
}