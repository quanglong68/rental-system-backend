package com.rental.propertiesservice.features.room.application;

import com.rental.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import com.rental.propertiesservice.features.events.PropertyEventPublisher;
import com.rental.propertiesservice.features.roomoccupant.application.port.out.RoomOccupantRepository;
import com.rental.propertiesservice.features.roomoccupant.infrastructure.persistence.ProjectionCheckpointRepository;
import com.rental.propertiesservice.features.building.application.BuildingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
        var room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> new IllegalStateException("Thiếu thông tin phòng"));
        buildingService.refreshOccupancyStatus(room.getBuildingId());
        if ("MAINTENANCE".equals(room.getStatus())) return;
        LocalDate today = LocalDate.now(clock);
        var states = checkpoints.forRoom(roomId);
        if (states.isEmpty()) return;

        // Spec 9.2: ContractSigned -> OCCUPIED, MoveOutApproved -> UPCOMING_VACANT,
        // ContractEnded -> VACANT unless another contract is already queued for the room.
        // An active occupant is enough evidence of occupancy even when the projection never
        // observed ContractSigned (e.g. history replayed from TenantAdded events).
        boolean occupiedNow = occupants.countAt(roomId, today) > 0;
        boolean signedEver = states.stream().anyMatch(c -> c.signedStartDate() != null);
        if (!signedEver && !occupiedNow) {
            // Never infer VACANT from an incomplete projection: a future arrival or a
            // TenantRemoved without ContractSigned must leave the room status untouched.
            return;
        }
        var open = states.stream()
                .filter(c -> c.endedDate() == null)
                .toList();
        String target;
        if (!open.isEmpty()) {
            boolean upcoming = occupants.countFuture(roomId, today) == 0
                    && open.stream().allMatch(c -> c.approvedMoveOutDate() != null
                            && !c.approvedMoveOutDate().isBefore(today));
            target = upcoming ? "UPCOMING_VACANT" : "OCCUPIED";
        } else {
            boolean nextPending = states.stream()
                    .filter(c -> c.endedDate() != null)
                    .max(Comparator.comparing(ProjectionCheckpointRepository.Checkpoint::endedDate))
                    .map(ProjectionCheckpointRepository.Checkpoint::nextContractExists)
                    .orElse(false);
            if (nextPending) return; // The queued contract owns the room status until it ends.
            target = "VACANT";
        }

        String previous = room.getStatus();
        if (previous.equals(target)) return;
        room.update(room.getRoomTypeId(), room.getRoomCode(), room.getMaxCapacity(), target,
                room.getArea(), room.getBedroomCount(), room.getBathroomCount(), room.getBasePrice(), clock.instant());
        rooms.saveAndFlush(room);
        events.roomStatusChanged(room, previous);
    }
}
