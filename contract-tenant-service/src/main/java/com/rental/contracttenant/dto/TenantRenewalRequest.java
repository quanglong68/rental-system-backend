package com.rental.contracttenant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantRenewalRequest {

    @NotNull(message = "termMonths là bắt buộc")
    private Integer termMonths; // 6 hoặc 12 tháng

    private String note;
}
