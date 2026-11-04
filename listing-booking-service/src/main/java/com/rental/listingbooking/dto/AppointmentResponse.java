package com.rental.listingbooking.dto;

import java.time.Instant;

public record AppointmentResponse(
    Long id,
    Long rentalRequestId,  // Optional - null for customer self-booking
    Long customerId,
    Long staffId,          // Optional - null until SALE confirms
    Long buildingId,       // Optional - derived from room_id when SALE confirms
    Instant appointmentDate,
    String status,
    Instant createdAt,
    Instant readAt,
    Long roomId            // Optional - for customer self-booking
) {}
