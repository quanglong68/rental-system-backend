package com.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request dang ky CUSTOMER (docs muc 7.1, Table 7).
 * username phai la email hoac SDT va trung voi email/phone khai bao (service kiem tra).
 * fullName/email/phone KHONG luu auth_db (thuoc User Service, C6 forward qua outbox).
 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Không được để trống")
    private String username;

    @NotBlank(message = "Không được để trống")
    @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự")
    private String password;

    @NotBlank(message = "Không được để trống")
    private String fullName;

    private String email;

    private String phone;
}
