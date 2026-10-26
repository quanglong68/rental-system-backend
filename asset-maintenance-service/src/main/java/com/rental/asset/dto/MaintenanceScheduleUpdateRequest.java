package com.rental.asset.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MaintenanceScheduleUpdateRequest(
        @NotNull Long staffId,
        @NotBlank String title,
        String description,
        @Min(1) Integer intervalMonths,
        @NotNull LocalDate nextDate) {}
