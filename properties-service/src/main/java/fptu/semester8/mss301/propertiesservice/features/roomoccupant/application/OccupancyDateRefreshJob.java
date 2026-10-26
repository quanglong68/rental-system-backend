package fptu.semester8.mss301.propertiesservice.features.roomoccupant.application;

import fptu.semester8.mss301.propertiesservice.features.room.application.RoomOccupancyStatusPolicy;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.infrastructure.persistence.ProjectionCheckpointRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OccupancyDateRefreshJob {

    static Logger log = LoggerFactory.getLogger(OccupancyDateRefreshJob.class);

    ProjectionCheckpointRepository checkpoints;

    RoomOccupancyStatusPolicy policy;

    // Fixed delay also repairs date transitions missed while the service was stopped.
    @Scheduled(fixedDelayString = "${property.occupancy.refresh-delay-ms:60000}")
    public void refreshDates() {
        long after = 0;
        while (true) {
            var ids = checkpoints.roomIdsAfter(after, 100);
            if (ids.isEmpty()) return;
            for (long roomId : ids) {
                try {
                    policy.refresh(roomId);
                } catch (RuntimeException failure) {
                    log.warn("Occupancy date refresh failed roomId={}; retry next run", roomId, failure);
                }
            }
            after = ids.getLast();
        }
    }
}
