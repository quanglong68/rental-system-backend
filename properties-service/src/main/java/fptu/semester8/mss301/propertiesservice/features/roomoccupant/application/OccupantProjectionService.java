package fptu.semester8.mss301.propertiesservice.features.roomoccupant.application;

import fptu.semester8.mss301.propertiesservice.common.id.IdGenerator;
import fptu.semester8.mss301.propertiesservice.features.events.InvalidEventException;
import fptu.semester8.mss301.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import fptu.semester8.mss301.propertiesservice.features.room.application.RoomOccupancyStatusPolicy;
import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingReferenceRepository;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.port.out.RoomOccupantRepository;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain.*;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.infrastructure.persistence.ProjectionCheckpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class OccupantProjectionService {
    static Logger log = LoggerFactory.getLogger(OccupantProjectionService.class);
    RoomRepository roomRepository;
    BuildingReferenceRepository buildings;
    RoomOccupantRepository roomOccupantRepository;
    ProjectionCheckpointRepository checkpoints;
    RoomOccupancyStatusPolicy statusPolicy;
    IdGenerator ids;
    Clock clock;

    public record Change(UUID eventId, String type, long contractId, long roomId, Long buildingId,
                         Long assignmentId, Long customerId, LocalDate date, long revision,
                         boolean nextContractExists) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Change event) {
        // A room lock serializes different contract streams affecting the same room.
        var room = roomRepository.findByIdForUpdate(event.roomId())
                .orElseThrow(() -> new IllegalStateException("Projection room is missing; retry/reconcile"));
        buildings.lockBuilding(room.getBuildingId());
        if (!buildings.buildingExists(room.getBuildingId()))
            throw new IllegalStateException("Projection building is deleted or missing; reconcile");
        if (event.buildingId() != null && !event.buildingId().equals(room.getBuildingId()))
            throw new InvalidEventException("Contract event buildingId does not match room");
        var checkpoint = checkpoints.lock(event.contractId(), event.roomId());
        if (checkpoint.roomId() != event.roomId())
            throw new InvalidEventException("Contract room transfer requires a new agreed event contract");
        if (event.revision() <= checkpoint.revision()) {
            log.info("Ignoring stale projection eventId={} contractId={} sourceRevision={} checkpoint={}",
                    event.eventId(), event.contractId(), event.revision(), checkpoint.revision());
            return;
        }
        if (event.revision() != checkpoint.revision() + 1)
            throw new IllegalStateException("Contract projection revision gap; retry then DLT/reconcile");
        if (checkpoint.endedDate() != null)
            throw new InvalidEventException("A terminated contract projection cannot be reopened");

        switch (event.type()) {
            case "TenantAdded" -> handleTenantAdded(event);
            case "TenantRemoved" -> handleTenantRemoved(event);
            case "ContractEnded" -> handleContractEnded(event);
            case "ContractSigned", "MoveOutApproved",
                 "ContractRenewed" -> { /* No assignment identity: checkpoint only. */ }
            default -> throw new InvalidEventException("Unsupported projection event");
        }
        checkpoints.advance(checkpoint, event.revision(), event.eventId(), event.type(), event.date(), event.nextContractExists());
        statusPolicy.refresh(event.roomId());
    }

    private void handleTenantAdded(Change e) {
        var existing = roomOccupantRepository.byAssignment(e.assignmentId());
        if (existing.isPresent()) {
            var row = existing.get();
            requireIdentity(row, e);
            if (!row.getMoveInDate().equals(e.date()) || row.getStatus() != RoomOccupantStatus.ACTIVE)
                throw new InvalidEventException("Assignment identity cannot be reused; rejoining requires a new contractTenantId");
            row.recordRevision(e.revision(), clock.instant());
            roomOccupantRepository.save(row);
            return;
        }
        roomOccupantRepository.save(new RoomOccupant(ids.nextId(), e.assignmentId(), e.roomId(), e.contractId(),
                e.customerId(), e.date(), e.revision(), clock.instant()));
    }

    private void handleTenantRemoved(Change e) {
        var row = roomOccupantRepository.byAssignment(e.assignmentId())
                .orElseThrow(() -> new IllegalStateException("Assignment missing; retry then DLT/reconcile"));
        requireIdentity(row, e);
        if (row.getStatus() == RoomOccupantStatus.ENDED && !e.date().equals(row.getMoveOutDate()))
            throw new InvalidEventException("Conflicting departure for ended assignment");
        end(row, e);
    }

    private void handleContractEnded(Change e) {
        for (var row : roomOccupantRepository.byContract(e.contractId())) {
            if (row.getRoomId() != e.roomId()) throw new InvalidEventException("Contract projection room mismatch");
            if (row.getStatus() == RoomOccupantStatus.ACTIVE) end(row, e);
        }
    }

    private void end(RoomOccupant row, Change e) {
        if (row.getSourceRevision() != null && row.getSourceRevision() >= e.revision())
            throw new InvalidEventException("Assignment revision is ahead of its contract checkpoint; reconcile");
        if (e.date().isBefore(row.getMoveInDate()))
            throw new InvalidEventException("Departure precedes assignment arrival");
        row.end(e.date(), e.revision(), clock.instant());
        roomOccupantRepository.save(row);
    }

    private void requireIdentity(RoomOccupant row, Change e) {
        if (!Objects.equals(row.getContractId(), e.contractId()) || !Objects.equals(row.getRoomId(), e.roomId())
                || !Objects.equals(row.getUserId(), e.customerId()))
            throw new InvalidEventException("Assignment identity conflicts with its existing projection");
    }
}
