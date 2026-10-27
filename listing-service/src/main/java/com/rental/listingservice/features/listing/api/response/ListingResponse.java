package com.rental.listingservice.features.listing.api.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.Instant;

public record ListingResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long roomId,
        @JsonSerialize(using = ToStringSerializer.class) Long buildingId,
        String buildingName,
        String address,
        @JsonSerialize(using = ToStringSerializer.class) Long wardId,
        @JsonSerialize(using = ToStringSerializer.class) Long districtId,
        @JsonSerialize(using = ToStringSerializer.class) Long roomTypeId,
        String roomTypeName,
        String roomNumber,
        Integer maxCapacity,
        BigDecimal price,
        BigDecimal latitude,
        BigDecimal longitude,
        String roomStatus,
        String title,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
