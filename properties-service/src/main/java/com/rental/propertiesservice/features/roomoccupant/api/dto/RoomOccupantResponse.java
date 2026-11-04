package com.rental.propertiesservice.features.roomoccupant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.rental.propertiesservice.features.roomoccupant.domain.RoomOccupantStatus;
import java.time.*;

public record RoomOccupantResponse(
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string") Long id,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string") Long contractTenantId,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string") Long roomId,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string") Long contractId,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string") Long userId,
        LocalDate moveInDate, LocalDate moveOutDate, RoomOccupantStatus status,
        Long sourceRevision, Instant createdAt, Instant updatedAt) {}
