package com.rental.listingbooking.dto;

public record ChatRentalRequestContext(
        Long rentalRequestId,
        Long customerId,
        Long listingId,
        Long buildingId,
        Long roomId,
        String status,
        Long verifiedBy,
        String roomNumber,
        String buildingName) {
}
