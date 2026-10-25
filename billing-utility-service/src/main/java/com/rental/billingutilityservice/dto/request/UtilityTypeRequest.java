package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Request tao/sua loai tien ich (docs muc 9.5). */
@Data
public class UtilityTypeRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String unit;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal unitPrice;
}
