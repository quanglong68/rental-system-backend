package com.rental.propertiesservice.features.building.application.exception;

import com.rental.common.error.NotFoundException;

public class WardNotFoundException extends NotFoundException {
    public WardNotFoundException(long wardId) {
        super("WARD_NOT_FOUND", "Ward %d was not found".formatted(wardId));
    }
}
