package com.rental.listingbooking.outbox;

public record RentalRequestCreatedPayload(
    Long rentalRequestId,
    Long listingId,
    Long roomId,
    Long buildingId,
    Long customerId
) {}
