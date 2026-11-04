package com.rental.propertiesservice.features.province.api.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ProvinceResponse(
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string", example = "898785309339156480") Long provinceId,
        String code,
        String name,
        String divisionType,
        Instant createdAt,
        Instant updatedAt) {
}
