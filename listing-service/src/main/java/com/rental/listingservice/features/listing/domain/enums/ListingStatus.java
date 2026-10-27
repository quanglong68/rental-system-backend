package com.rental.listingservice.features.listing.domain.enums;

import java.util.Arrays;

public enum ListingStatus {
    ACTIVE("ACTIVE"),
    CLOSED("CLOSED");

    private final String value;

    ListingStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ListingStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.getValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported listing status: " + value));
    }
}
