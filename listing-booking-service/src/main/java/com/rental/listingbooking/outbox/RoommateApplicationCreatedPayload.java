package com.rental.listingbooking.outbox;

public record RoommateApplicationCreatedPayload(
    Long applicationId,
    Long postId,
    Long ownerCustomerId,
    Long applicantCustomerId
) {}
