package com.rental.listingbooking.outbox;

import java.time.Instant;

public record AppointmentCreatedPayload(
    Long appointmentId,
    Long rentalRequestId,
    Long customerId,
    Long staffId,
    Long buildingId,
    Instant appointmentDate
) {}
