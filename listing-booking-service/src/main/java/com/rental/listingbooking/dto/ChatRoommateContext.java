package com.rental.listingbooking.dto;

public record ChatRoommateContext(
        Long applicationId,
        Long postId,
        Long ownerCustomerId,
        Long applicantCustomerId,
        String postTitle,
        Long roomId,
        Long buildingId,
        String roomNumber,
        String buildingName) {
}
