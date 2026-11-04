package com.rental.listingbooking.outbox;

import java.time.LocalDate;

public record RoommateApplicationAcceptedPayload(
    Long applicationId,
    Long postId,
    Long roomId,
    Long buildingId,
    Long ownerCustomerId,
    Long applicantCustomerId,
    String fullName,
    String phone,
    String cccdNumber,
    LocalDate moveInDate
) {}

