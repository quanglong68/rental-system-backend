package fptu.semester8.mss301.propertiesservice.features.room.application.port.out;

import fptu.semester8.mss301.propertiesservice.features.room.application.model.RoomStatistics;
import java.time.LocalDate;

public interface RoomStatisticsRepository {
    RoomStatistics summarize(Long buildingId, LocalDate date);
}
