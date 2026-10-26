package fptu.semester8.mss301.propertiesservice.features.building.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.Instant;

public record BuildingSearchResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(type = "string", example = "898785309339156480") Long buildingId,
        String name,
        String address,
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(type = "string", example = "898785309339156482") Long provinceId,
        String provinceName,
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(type = "string", example = "898785309339156481") Long wardId,
        String wardName,
        BigDecimal latitude,
        BigDecimal longitude,
        String description,
        String buildingStatus,
        Instant createdAt,
        Instant updatedAt,
        Double distanceMeters,
        @Schema(description = "URL của ảnh đầu tiên theo displayOrder; null nếu tòa nhà chưa có ảnh")
        String buildingUrlImage
) {
}
