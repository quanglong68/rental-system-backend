package com.rental.propertiesservice.features.building.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

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
        String lessorName,
        LocalDate lessorDateOfBirth,
        String lessorIdNumber,
        LocalDate lessorIdIssueDate,
        String lessorIdIssuePlace,
        String lessorPhone,
        String lessorAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public BuildingResponse(Long buildingId, String name, String address, Long wardId, Long provinceId,
                            BigDecimal latitude, BigDecimal longitude, String description,
                            String buildingStatus, String lessorName, String lessorPhone) {
        this(buildingId, name, address, wardId, provinceId, latitude, longitude, description,
                buildingStatus, lessorName, null, null, null, null, lessorPhone, null, null, null);
    }
}
