package fptu.semester8.mss301.propertiesservice.features.room.domain;

import lombok.Getter;

@Getter
public enum RoomStatus {
    VACANT("VACANT"),
    UPCOMING_VACANT("UPCOMING_VACANT"),
    OCCUPIED("OCCUPIED"),
    MAINTENANCE("MAINTENANCE");

    private final String value;

    RoomStatus(String value) {
        this.value = value;
    }
}
