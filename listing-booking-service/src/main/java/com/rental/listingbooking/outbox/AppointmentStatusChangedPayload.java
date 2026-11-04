package com.rental.listingbooking.outbox;

public record AppointmentStatusChangedPayload(
    Long appointmentId,
    Long customerId,
    Long staffId,
    Long buildingId,
    String status
) {}
