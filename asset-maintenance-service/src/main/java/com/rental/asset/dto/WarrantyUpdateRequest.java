package com.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record WarrantyUpdateRequest(
        @NotBlank String provider,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {}
