package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request chot so thu cong (docs muc 9.5): {month, year, buildingId?}. */
@Data
public class GenerateInvoiceRequest {

    @NotNull
    @Min(1)
    @Max(12)
    private Integer month;

    @NotNull
    @Min(2000)
    @Max(2100)
    private Integer year;

    private Long buildingId;
}
