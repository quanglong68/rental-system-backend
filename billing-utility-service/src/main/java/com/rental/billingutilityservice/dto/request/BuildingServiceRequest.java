package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Request tao/sua dich vu toa nha (docs muc 9.5): {buildingId, name, unit, unitPrice}. */
@Data
public class BuildingServiceRequest {

    @NotNull
    private Long buildingId;

    @NotBlank
    private String name;

    private String unit;

    @NotNull
    @DecimalMin("0")
    private BigDecimal unitPrice;
}
