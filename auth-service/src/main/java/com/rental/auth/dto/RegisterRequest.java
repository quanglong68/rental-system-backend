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

    @NotBlank(message = "Khong duoc de trong")
    private String username;

    @NotBlank(message = "Khong duoc de trong")
    @Size(min = 8, message = "Mat khau toi thieu 8 ky tu")
    private String password;

    @NotBlank(message = "Khong duoc de trong")
    private String fullName;

    private String email;

    private String phone;
}
