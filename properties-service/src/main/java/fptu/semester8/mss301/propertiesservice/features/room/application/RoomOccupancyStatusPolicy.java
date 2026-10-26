package fptu.semester8.mss301.propertiesservice.features.room.application;

import fptu.semester8.mss301.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import fptu.semester8.mss301.propertiesservice.features.events.PropertyEventPublisher;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.port.out.RoomOccupantRepository;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.infrastructure.persistence.ProjectionCheckpointRepository;
import fptu.semester8.mss301.propertiesservice.features.building.application.BuildingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomOccupancyStatusPolicy {

    BuildingService buildingService;

    RoomRepository rooms;

    RoomOccupantRepository occupants;
    
    ProjectionCheckpointRepository checkpoints;

    PropertyEventPublisher events;

    Clock clock;

    @Transactional
    public void refresh(long roomId) {
        var room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> new IllegalStateException("Room missing"));
        buildingService.refreshOccupancyStatus(room.getBuildingId());
        if ("MAINTENANCE".equals(room.getStatus())) return;
        LocalDate today = LocalDate.now(clock);
        var current = occupants.current(roomId, today, Pageable.unpaged()).getContent();
        var states = checkpoints.forRoom(roomId);
        var live = states.stream().filter(c -> c.endedDate() == null || !c.endedDate().isBefore(today)).toList();
        boolean signedNow = live.stream().anyMatch(c -> c.signedStartDate() != null && !c.signedStartDate().isAfter(today));
        boolean next = states.stream().anyMatch(ProjectionCheckpointRepository.Checkpoint::nextContractExists);
        if (current.isEmpty() && !signedNow && !next) return; // Absence is not a complete room vacancy snapshot.
        boolean upcoming = !live.isEmpty() && !next && occupants.countFuture(roomId, today) == 0
                && live.stream().allMatch(c -> c.approvedMoveOutDate() != null && !c.approvedMoveOutDate().isBefore(today))
                && current.stream().allMatch(o -> live.stream().anyMatch(c -> Objects.equals(o.getContractId(), c.contractId())));
        String target = upcoming ? "UPCOMING_VACANT" : "OCCUPIED";
        String previous = room.getStatus();
        if (previous.equals(target)) return;
        room.update(room.getRoomTypeId(), room.getRoomCode(), room.getMaxCapacity(), target,
                room.getArea(), room.getBedroomCount(), room.getBathroomCount(), room.getBasePrice(), clock.instant());
        rooms.saveAndFlush(room);
        events.roomStatusChanged(room, previous);
    }
}
