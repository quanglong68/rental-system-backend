package com.rental.propertiesservice.features.building.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BuildingCommand(
        String name,
        String address,
        Long wardId,
        Long provinceId,
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
        String lessorAddress
) {
    public BuildingCommand(String name, String address, Long wardId, Long provinceId,
                           BigDecimal latitude, BigDecimal longitude, String description) {
        this(name, address, wardId, provinceId, latitude, longitude, description, null,
                null, null, null, null, null, null, null);
    }

    public BuildingCommand(String name, String address, Long wardId, Long provinceId,
                           BigDecimal latitude, BigDecimal longitude, String description,
                           String buildingStatus) {
        this(name, address, wardId, provinceId, latitude, longitude, description, buildingStatus,
                null, null, null, null, null, null, null);
    }
}
