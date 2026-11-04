package com.rental.listingbooking.outbox;

public record RentalRequestRejectedPayload(
    Long rentalRequestId,
    Long customerId,
    String reason
) {}
