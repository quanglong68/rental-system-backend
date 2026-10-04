package com.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request dang nhap (docs muc 7.1, Table 7). username chuan hoa trim + lowercase.
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Khong duoc de trong")
    private String username;

    @NotBlank(message = "Khong duoc de trong")
    private String password;
}