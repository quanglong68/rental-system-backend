package com.rental.listingbooking.dto;

public record ChatListingContext(
        Long listingId,
        Long buildingId,
        Long roomId,
        String roomNumber,
        String buildingName,
        String title) {
}
