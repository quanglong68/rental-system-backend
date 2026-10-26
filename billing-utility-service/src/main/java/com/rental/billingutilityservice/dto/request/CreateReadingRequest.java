package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request nhap chi so (docs muc 9.5): {meterId, month, year, currentIndex, readingDate?}. */
@Data
public class CreateReadingRequest {

    @NotNull
    private Long meterId;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer month;

    @NotNull
    @Min(2000)
    @Max(2100)
    private Integer year;

    @NotNull
    private BigDecimal currentIndex;

    private LocalDate readingDate;
}
