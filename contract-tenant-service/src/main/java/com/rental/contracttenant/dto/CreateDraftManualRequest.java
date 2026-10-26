package com.rental.contracttenant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request for direct DRAFT contract creation by SALE/QUAN_LY/ADMIN.
 * No rentalRequestId or listingId - manual workflow, not event-driven.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateDraftManualRequest {

    @NotNull @Positive
    private Long customerId;

    @NotNull @Positive
    private Long roomId;

    @NotNull @Positive
    private Long buildingId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 15, fraction = 0)
    private BigDecimal rentAmount;
}
