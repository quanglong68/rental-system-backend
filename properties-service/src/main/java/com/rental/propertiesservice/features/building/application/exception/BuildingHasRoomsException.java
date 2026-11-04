package com.rental.propertiesservice.features.building.application.exception;

import com.rental.common.error.BusinessException;
import org.springframework.http.HttpStatus;

public class BuildingHasRoomsException extends BusinessException {
    public BuildingHasRoomsException(long buildingId) {
        super("BUILDING_HAS_ROOMS", "Building %d cannot be deleted while it has rooms".formatted(buildingId),
                HttpStatus.CONFLICT);
    }
}
