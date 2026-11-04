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

    @NotBlank(message = "Không được để trống")
    private String oldPassword;

    @NotBlank(message = "Không được để trống")
    @Size(min = 8, message = "Mật khẩu mới tối thiểu 8 ký tự")
    private String newPassword;
}