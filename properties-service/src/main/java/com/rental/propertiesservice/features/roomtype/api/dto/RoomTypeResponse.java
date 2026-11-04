package com.rental.propertiesservice.features.roomtype.api.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record RoomTypeResponse(
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156480") Long id,
        String typeCode,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
