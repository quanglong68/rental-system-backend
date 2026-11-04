package com.rental.listingbooking.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AppointmentCreateDto(
    Long rentalRequestId,  // Optional - only required when booking from existing request
    Long staffId,          // Required when sale creates for request
    Long listingId,        // Optional - for customer self-booking
    Long roomId,           // Optional - for customer self-booking (preferred)
    @NotNull(message = "Thời gian hẹn không được để trống")
    Instant appointmentDate
) {}
