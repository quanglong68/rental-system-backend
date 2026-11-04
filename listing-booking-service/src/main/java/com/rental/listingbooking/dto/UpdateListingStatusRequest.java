package com.rental.listingbooking.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateListingStatusRequest(
    @NotBlank(message = "Trạng thái không được để trống")
    String status
) {}
