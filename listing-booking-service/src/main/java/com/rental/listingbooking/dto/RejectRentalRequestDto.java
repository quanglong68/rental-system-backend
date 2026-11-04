package com.rental.listingbooking.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRentalRequestDto(
    @NotBlank(message = "Lý do từ chối không được để trống")
    String reason
) {}
