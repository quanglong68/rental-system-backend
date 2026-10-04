package com.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request xoa phien (logout) hoac refresh token: du dung mot kieu body.
 */
@Getter
@Setter
@NoArgsConstructor
public class RefreshRequest {

    @NotBlank(message = "Khong duoc de trong")
    private String refreshToken;
}