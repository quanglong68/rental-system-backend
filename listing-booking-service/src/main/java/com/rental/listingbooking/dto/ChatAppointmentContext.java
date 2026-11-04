package com.rental.listingbooking.dto;

import java.time.Instant;

public record ChatAppointmentContext(
        Long appointmentId,
        Long customerId,
        Long staffId,
        Long buildingId,
        Long roomId,
        Instant appointmentDate,
        String status,
        String roomNumber,
        String buildingName) {
}
