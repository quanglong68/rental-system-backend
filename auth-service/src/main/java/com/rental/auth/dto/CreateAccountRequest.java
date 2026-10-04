package com.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request ADMIN tao STAFF/ADMIN (Table 7). accountType chi nhan STAFF|ADMIN (service kiem tra).
 * STAFF bat buoc co roles; ADMIN duoc auto-gan role ADMIN, input roles bi bo qua.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateAccountRequest {

    @NotBlank(message = "Khong duoc de trong")
    private String username;

    @NotBlank(message = "Khong duoc de trong")
    @Size(min = 8, message = "Mat khau toi thieu 8 ky tu")
    private String password;

    @NotBlank(message = "Khong duoc de trong")
    private String fullName;

    private String email;

    private String phone;

    @NotNull(message = "Khong duoc de trong")
    private String accountType;

    private List<String> roles;
}
