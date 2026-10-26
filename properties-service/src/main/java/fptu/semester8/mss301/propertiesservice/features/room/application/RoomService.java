package fptu.semester8.mss301.propertiesservice.features.room.application;

import fptu.exe202.signify.apiresponse.exception.*;
import fptu.semester8.mss301.propertiesservice.common.id.IdGenerator;
import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingReferenceRepository;
import fptu.semester8.mss301.propertiesservice.features.room.api.dto.RoomRequest;
import fptu.semester8.mss301.propertiesservice.features.room.domain.*;
import fptu.semester8.mss301.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import fptu.semester8.mss301.propertiesservice.features.events.PropertyEventPublisher;
import fptu.semester8.mss301.propertiesservice.features.building.application.BuildingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomService {

    BuildingService buildingService;

    RoomRepository rooms;

    BuildingReferenceRepository buildings;

    IdGenerator ids;

    PropertyEventPublisher events;

    @Transactional
    public Room create(RoomRequest r) {
        String status = normalizeStatus(r.status());
        validateBuilding(r.buildingId());
        if (rooms.existsByBuildingIdAndRoomCode(r.buildingId(), r.roomCode().trim()))
            throw new ConflictException("Room code already exists in building", "ROOM_CODE_CONFLICT");
        Instant n = Instant.now();
        Room room = rooms.saveAndFlush(new Room(ids.nextId(), r.buildingId(), r.roomTypeId(), r.roomCode().trim(), r.maxCapacity(), RoomStatus.VACANT.getValue(), r.area(), r.bedroomCount(), r.bathroomCount(), r.basePrice(), n));
        events.roomStatusChanged(room, null);
        buildingService.refreshOccupancyStatus(room.getBuildingId());
        return room;
    }

    @Transactional(readOnly = true)
    public Room get(long id) {
        return rooms.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room %d was not found".formatted(id), "ROOM_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public Page<Room> list(Long buildingId, Pageable p) {
        return buildingId == null ? rooms.findAll(p) : rooms.findAllByBuildingId(buildingId, p);
    }

    @Transactional
    public Room update(long id, RoomRequest r) {
        String status = normalizeStatus(r.status());
        Room x = rooms.findByIdForUpdate(id).orElseThrow(() ->
                new ResourceNotFoundException("Room %d was not found".formatted(id), "ROOM_NOT_FOUND"));
        validateBuilding(r.buildingId());
        String code = r.roomCode().trim();
        if (rooms.existsByBuildingIdAndRoomCodeAndIdNot(r.buildingId(), code, id))
            throw new ConflictException("Room code already exists in building", "ROOM_CODE_CONFLICT");
        if (!Objects.equals(x.getStatus(), status) && !allowed(x.getStatus(), status))
            throw new BadRequestException("Invalid room status transition", "INVALID_ROOM_STATUS_TRANSITION");
        String previousStatus = x.getStatus();
        x.update(r.roomTypeId(), code, r.maxCapacity(), status, r.area(), r.bedroomCount(), r.bathroomCount(), r.basePrice(), Instant.now());
        Room saved = rooms.saveAndFlush(x);
        if (!Objects.equals(previousStatus, status)) {
            events.roomStatusChanged(saved, previousStatus);
        }
        buildingService.refreshOccupancyStatus(saved.getBuildingId());
        return saved;
    }

    @Transactional
    public void delete(long id) {
        Room room = get(id);
        buildings.lockBuilding(room.getBuildingId());
        rooms.delete(room);
        rooms.flush();
        buildingService.refreshOccupancyStatus(room.getBuildingId());
    }

    private void validateBuilding(long id) {
        buildings.lockBuilding(id);
        if (!buildings.buildingExists(id))
            throw new ResourceNotFoundException("Building %d was not found".formatted(id), "BUILDING_NOT_FOUND");
    }

    private String normalizeStatus(String status) {
        try {
            return RoomStatus.valueOf(status.trim().toUpperCase()).getValue();
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported room status", "INVALID_ROOM_STATUS");
        }
    }

    private boolean allowed(String a, String b) {
        return switch (a) {
            case "VACANT" -> Objects.equals(b, RoomStatus.OCCUPIED.getValue()) || Objects.equals(b, RoomStatus.MAINTENANCE.getValue());
            case "UPCOMING_VACANT" -> Objects.equals(b, RoomStatus.VACANT.getValue()) || Objects.equals(b, RoomStatus.OCCUPIED.getValue()) || Objects.equals(b, RoomStatus.MAINTENANCE.getValue());
            case "OCCUPIED" -> Objects.equals(b, RoomStatus.UPCOMING_VACANT.getValue()) || Objects.equals(b, RoomStatus.MAINTENANCE.getValue());
            case "MAINTENANCE" -> Objects.equals(b, RoomStatus.VACANT.getValue()) || Objects.equals(b, RoomStatus.UPCOMING_VACANT.getValue());
            default -> throw new ResourceNotFoundException("Unexpected value: " + a);
        };
    }
}
