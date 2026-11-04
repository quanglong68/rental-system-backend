package com.rental.propertiesservice.features.room.application;

import com.rental.common.error.BusinessException;
import com.rental.common.error.NotFoundException;
import com.rental.propertiesservice.common.id.IdGenerator;
import com.rental.propertiesservice.config.BuildingAccessGuard;
import com.rental.propertiesservice.features.building.application.port.out.BuildingReferenceRepository;
import com.rental.propertiesservice.features.room.api.dto.RoomRequest;
import com.rental.propertiesservice.features.room.domain.*;
import com.rental.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import com.rental.propertiesservice.features.roomoccupant.application.port.out.RoomOccupantRepository;
import com.rental.propertiesservice.features.events.PropertyEventPublisher;
import com.rental.propertiesservice.features.building.application.BuildingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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

    BuildingAccessGuard access;

    RoomOccupantRepository occupants;

    @Transactional
    public Room create(RoomRequest r) {
        access.assertAssigned(r.buildingId());
        String status = normalizeStatus(r.status());
        validateBuilding(r.buildingId());
        if (rooms.existsByBuildingIdAndRoomCode(r.buildingId(), r.roomCode().trim()))
            throw new BusinessException("ROOM_CODE_CONFLICT", "Mã phòng đã tồn tại trong tòa nhà", HttpStatus.CONFLICT);
        Instant n = Instant.now();
        Room room = rooms.saveAndFlush(new Room(ids.nextId(), r.buildingId(), r.roomTypeId(), r.roomCode().trim(), r.maxCapacity(), RoomStatus.VACANT.getValue(), r.area(), r.bedroomCount(), r.bathroomCount(), r.basePrice(), n));
        events.roomStatusChanged(room, null);
        buildingService.refreshOccupancyStatus(room.getBuildingId());
        return room;
    }

    @Transactional(readOnly = true)
    public Room get(long id) {
        return rooms.findById(id).orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng %d".formatted(id)));
    }

    @Transactional(readOnly = true)
    public Page<Room> list(Long buildingId, Pageable p) {
        if (buildingId != null) {
            access.assertAssigned(buildingId);
            return rooms.findAllByBuildingId(buildingId, p);
        }
        List<Long> allowed = access.allowedBuildingIdsOrNull();
        if (allowed == null) return rooms.findAll(p);
        if (allowed.isEmpty()) return Page.empty(p);
        return rooms.findAllByBuildingIdIn(allowed, p);
    }

    @Transactional
    public Room update(long id, RoomRequest r) {
        String status = normalizeStatus(r.status());
        Room x = rooms.findByIdForUpdate(id).orElseThrow(() ->
                new NotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng %d".formatted(id)));
        access.assertAssigned(x.getBuildingId());
        access.assertAssigned(r.buildingId());
        validateBuilding(r.buildingId());
        String code = r.roomCode().trim();
        if (rooms.existsByBuildingIdAndRoomCodeAndIdNot(r.buildingId(), code, id))
            throw new BusinessException("ROOM_CODE_CONFLICT", "Mã phòng đã tồn tại trong tòa nhà", HttpStatus.CONFLICT);
        if (!Objects.equals(x.getStatus(), status) && !allowed(x.getStatus(), status))
            throw new BusinessException("INVALID_ROOM_STATUS_TRANSITION", "Chuyển trạng thái phòng không hợp lệ", HttpStatus.BAD_REQUEST);
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
    public Room changeStatus(long id, String requestedStatus) {
        String status = normalizeStatus(requestedStatus);
        Room room = rooms.findByIdForUpdate(id).orElseThrow(() ->
                new NotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng %d".formatted(id)));
        access.assertAssigned(room.getBuildingId());
        String previousStatus = room.getStatus();
        if (!Objects.equals(previousStatus, status) && !allowed(previousStatus, status)) {
            throw new BusinessException("INVALID_ROOM_STATUS_TRANSITION", "Chuyển trạng thái phòng không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        room.update(room.getRoomTypeId(), room.getRoomCode(), room.getMaxCapacity(), status, room.getArea(),
                room.getBedroomCount(), room.getBathroomCount(), room.getBasePrice(), Instant.now());
        Room saved = rooms.saveAndFlush(room);
        if (!Objects.equals(previousStatus, status)) {
            events.roomStatusChanged(saved, previousStatus);
        }
        buildingService.refreshOccupancyStatus(saved.getBuildingId());
        return saved;
    }

    @Transactional
    public void delete(long id) {
        Room room = get(id);
        access.assertAssigned(room.getBuildingId());
        buildings.lockBuilding(room.getBuildingId());
        if (occupants.existsByRoomId(id)) {
            throw new BusinessException("ROOM_HAS_OCCUPANTS",
                    "Phòng đã có lịch sử người ở nên không thể xóa", HttpStatus.CONFLICT);
        }
        rooms.delete(room);
        rooms.flush();
        events.roomDeleted(room);
        buildingService.refreshOccupancyStatus(room.getBuildingId());
    }

    private void validateBuilding(long id) {
        buildings.lockBuilding(id);
        if (!buildings.buildingExists(id))
            throw new NotFoundException("BUILDING_NOT_FOUND", "Không tìm thấy tòa nhà %d".formatted(id));
    }

    private String normalizeStatus(String status) {
        try {
            return RoomStatus.valueOf(status.trim().toUpperCase()).getValue();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_ROOM_STATUS", "Trạng thái phòng không được hỗ trợ", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Spec 9.2: QUAN_LY may only switch manually between VACANT and MAINTENANCE. OCCUPIED,
     * UPCOMING_VACANT and the return to VACANT after move-out are decided by contract events, so a
     * manual write to any of them would fight the projection.
     */
    private boolean allowed(String a, String b) {
        return (RoomStatus.VACANT.getValue().equals(a) && RoomStatus.MAINTENANCE.getValue().equals(b))
                || (RoomStatus.MAINTENANCE.getValue().equals(a) && RoomStatus.VACANT.getValue().equals(b));
    }
}
