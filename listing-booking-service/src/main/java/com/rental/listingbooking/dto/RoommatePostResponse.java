package com.rental.listingbooking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RoommatePostResponse(
    Long id,
    Long customerId,
    Long roomId,
    Long buildingId,
    String title,
    String description,
    BigDecimal budgetShare,
    String status,
    Instant createdAt
) {}
