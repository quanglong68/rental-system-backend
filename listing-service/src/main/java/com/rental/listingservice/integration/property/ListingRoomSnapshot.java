package com.rental.listingservice.integration.property;

import java.math.BigDecimal;

public record ListingRoomSnapshot(
        Long roomId,
        Long buildingId,
        String buildingName,
        String address,
        Long wardId,
        Long districtId,
        Long roomTypeId,
        String roomTypeName,
        String roomNumber,
        Integer maxCapacity,
        String roomStatus,
        BigDecimal price,
        BigDecimal latitude,
        BigDecimal longitude) {
}
