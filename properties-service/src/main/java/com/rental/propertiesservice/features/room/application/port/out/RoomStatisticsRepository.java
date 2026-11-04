package com.rental.propertiesservice.features.room.application.port.out;

import com.rental.propertiesservice.features.room.application.model.RoomStatistics;
import java.time.LocalDate;

public interface RoomStatisticsRepository {
    RoomStatistics summarize(Long buildingId, LocalDate date);
}
