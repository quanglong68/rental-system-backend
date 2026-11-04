package com.rental.listingbooking.dto;

public record ChatRoommatePostContext(
        Long postId,
        Long ownerCustomerId,
        String postTitle,
        Long roomId,
        Long buildingId,
        String roomNumber,
        String buildingName) {
}
