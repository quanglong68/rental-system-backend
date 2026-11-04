package com.rental.propertiesservice.features.roomoccupant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;

public record OccupancySummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(type = "string")
        Long roomId,
        int maxCapacity,
        long currentOccupants,
        long availableCapacity,
        boolean overCapacity,
        String roomStatus,
        LocalDate businessDate
) {
}
