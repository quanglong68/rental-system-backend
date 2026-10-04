package com.rental.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request doi role cho STAFF: PUT /accounts/{id}/roles.
 * Chi chap nhan QUAN_LY/KY_THUAT/SALE, sai thi 422 ROLE_NOT_ALLOWED.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateAccountRolesRequest {

    @NotEmpty(message = "Phai co it nhat 1 role")
    private List<String> roles;
}
