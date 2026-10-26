package fptu.semester8.mss301.propertiesservice.features.room.application.model;

import java.time.LocalDate;

public record RoomStatistics(long totalRooms, long operatingRooms, long roomsNeedingUpdate,
                             LocalDate businessDate) {}
