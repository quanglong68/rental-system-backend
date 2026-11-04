package com.rental.listingbooking.outbox;

public record RoommateApplicationRejectedPayload(
    Long applicationId,
    Long postId,
    Long applicantCustomerId
) {}
