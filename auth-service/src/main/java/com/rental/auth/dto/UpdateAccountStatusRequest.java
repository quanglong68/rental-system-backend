package com.rental.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request khoa/mo khoa tai khoan: PUT /accounts/{id}/status.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateAccountStatusRequest {

    @NotNull(message = "Khong duoc de trong")
    private Boolean active;
}
