package com.rental.listingbooking.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAppointmentStatusDto(
    @NotBlank(message = "Trạng thái không được để trống")
    String status
) {}
