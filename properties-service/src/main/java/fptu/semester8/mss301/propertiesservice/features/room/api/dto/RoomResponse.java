package fptu.semester8.mss301.propertiesservice.features.room.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

public record RoomResponse(
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156480") Long id,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156481") Long buildingId,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156482") Long roomTypeId,
        String roomCode,
        Integer maxCapacity,
        String status,
        BigDecimal area,
        Integer bedroomCount,
        Integer bathroomCount,
        BigDecimal basePrice, Instant createdAt,
        Instant updatedAt
) {
}
