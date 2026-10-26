package fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain;

import lombok.Getter;

@Getter
public enum RoomOccupantStatus {

    ACTIVE("ACTIVE"),
    ENDED("ENDED");

    RoomOccupantStatus(String value) {
        this.value = value;
    }

    private final String value;
}
