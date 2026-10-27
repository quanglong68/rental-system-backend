package com.rental.listingservice.features.listing.domain.enums;

import java.util.Arrays;

public enum RoomStatus {
    VACANT("VACANT"),
    UPCOMING_VACANT("UPCOMING_VACANT"),
    OCCUPIED("OCCUPIED"),
    MAINTENANCE("MAINTENANCE");

    private final String value;

    RoomStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RoomStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.getValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported room status: " + value));
    }
}
