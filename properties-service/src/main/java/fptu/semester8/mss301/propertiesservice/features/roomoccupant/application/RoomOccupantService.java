package fptu.semester8.mss301.propertiesservice.features.roomoccupant.application;

import fptu.exe202.signify.apiresponse.exception.ResourceNotFoundException;
import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingReferenceRepository;
import fptu.semester8.mss301.propertiesservice.features.room.application.RoomService;
import fptu.semester8.mss301.propertiesservice.features.room.domain.Room;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.port.out.RoomOccupantRepository;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.api.dto.*;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.mapper.RoomOccupantMapper;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class RoomOccupantService {

    RoomOccupantRepository occupants;

    RoomService rooms;

    BuildingReferenceRepository buildings;

    RoomOccupantMapper mapper;

    Clock clock;

    public Page<RoomOccupantResponse> current(long roomId, Pageable page) {
        requireRoom(roomId);
        return occupants.current(roomId, LocalDate.now(clock), page).map(mapper::toResponse);
    }

    public Page<RoomOccupantResponse> history(long roomId, Pageable page) {
        requireRoom(roomId);
        return occupants.history(roomId, page).map(mapper::toResponse);
    }

    public Page<RoomOccupantResponse> byUser(long userId, Pageable page) {
        return occupants.byUser(userId, page).map(mapper::toResponse);
    }

    public RoomOccupantResponse detail(long roomId, long id) {
        requireRoom(roomId);
        return occupants.byId(id).filter(o -> o.getRoomId() == roomId).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Room occupant was not found", "ROOM_OCCUPANT_NOT_FOUND"));
    }

    public OccupancySummaryResponse summary(long roomId, LocalDate date) {
        Room room = requireRoom(roomId);
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        long count = occupants.countAt(roomId, day);
        long available = room.getMaxCapacity() - count;
        return new OccupancySummaryResponse(roomId, room.getMaxCapacity(), count, available, available < 0, room.getStatus(), day);
    }

    private Room requireRoom(long id) {
        Room room = rooms.get(id);
        if (!buildings.buildingExists(room.getBuildingId()))
            throw new ResourceNotFoundException("Room was not found", "ROOM_NOT_FOUND");
        return room;
    }
}
