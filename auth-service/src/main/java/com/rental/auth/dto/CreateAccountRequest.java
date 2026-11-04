package com.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request ADMIN tao STAFF/ADMIN (Table 7). accountType chỉ nhận STAFF|ADMIN (service kiem tra).
 * STAFF bat buoc co roles; ADMIN duoc auto-gan role ADMIN, input roles bi bo qua.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateAccountRequest {

    @NotBlank(message = "Không được để trống")
    private String username;

    @NotBlank(message = "Không được để trống")
    @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự")
    private String password;

    @NotBlank(message = "Không được để trống")
    private String fullName;

    private String email;

    private String phone;

    @NotNull(message = "Không được để trống")
    private String accountType;

    private List<String> roles;
}
