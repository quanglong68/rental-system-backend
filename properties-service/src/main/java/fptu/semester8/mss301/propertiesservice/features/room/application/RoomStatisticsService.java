package fptu.semester8.mss301.propertiesservice.features.room.application;

import fptu.semester8.mss301.propertiesservice.features.room.application.model.RoomStatistics;
import fptu.semester8.mss301.propertiesservice.features.room.application.port.out.RoomStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RoomStatisticsService {
    private final RoomStatisticsRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RoomStatistics summarize(Long buildingId) {
        return repository.summarize(buildingId, LocalDate.now(clock));
    }
}
