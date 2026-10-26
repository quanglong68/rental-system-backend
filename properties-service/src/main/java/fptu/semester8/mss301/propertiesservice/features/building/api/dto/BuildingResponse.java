package fptu.semester8.mss301.propertiesservice.features.building.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

public record BuildingResponse(
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156480") Long buildingId,
        String name,
        String address,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156481") Long wardId,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156482") Long provinceId,
        BigDecimal latitude,
        BigDecimal longitude,
        String description,
        String buildingStatus,
        Instant createdAt,
        Instant updatedAt
) {}
