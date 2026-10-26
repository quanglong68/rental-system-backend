package fptu.semester8.mss301.propertiesservice.features.building.domain;

import lombok.Getter;

@Getter
public enum BuildingStatus {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    MAINTENANCE("MAINTENANCE"),
    CLOSED("CLOSED");

    private final String value;

    BuildingStatus(String value) {
        this.value = value;
    }
}
