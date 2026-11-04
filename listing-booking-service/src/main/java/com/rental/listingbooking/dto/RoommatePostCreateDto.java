package com.rental.listingbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RoommatePostCreateDto(
    @NotNull(message = "ID phòng không được để trống")
    Long roomId,
    @NotBlank(message = "Tiêu đề không được để trống")
    String title,
    String description,
    BigDecimal budgetShare
) {}
