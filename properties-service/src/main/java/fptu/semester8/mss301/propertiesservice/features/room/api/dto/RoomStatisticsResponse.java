package fptu.semester8.mss301.propertiesservice.features.room.api.dto;

import java.time.LocalDate;

public record RoomStatisticsResponse(long totalRooms, long operatingRooms, long roomsNeedingUpdate,
                                     LocalDate businessDate) {}
