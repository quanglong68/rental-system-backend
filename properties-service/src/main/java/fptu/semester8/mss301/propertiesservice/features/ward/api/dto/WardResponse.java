package fptu.semester8.mss301.propertiesservice.features.ward.api.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

public record WardResponse(
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156480") Long wardId,
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156481") Long provinceId,
        String code,
        String name,
        String divisionType,
        Instant createdAt,
        Instant updatedAt
) {
}
