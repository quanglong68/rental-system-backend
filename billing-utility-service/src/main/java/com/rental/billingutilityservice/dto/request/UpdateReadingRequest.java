package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Request sua chi so (docs muc 9.5): {currentIndex}. */
@Data
public class UpdateReadingRequest {

    @NotNull
    private BigDecimal currentIndex;
}
