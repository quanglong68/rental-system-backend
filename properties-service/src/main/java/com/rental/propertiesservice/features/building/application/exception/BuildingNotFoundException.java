package com.rental.propertiesservice.features.building.application.exception;

import com.rental.common.error.NotFoundException;

public class BuildingNotFoundException extends NotFoundException {
    public BuildingNotFoundException(long buildingId) {
        super("BUILDING_NOT_FOUND", "Không tìm thấy tòa nhà %d".formatted(buildingId));
    }
}
