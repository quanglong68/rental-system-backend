package fptu.semester8.mss301.propertiesservice.features.listingintegration.application;

import fptu.semester8.mss301.propertiesservice.features.building.application.BuildingService;
import fptu.semester8.mss301.propertiesservice.features.room.application.RoomService;
import fptu.semester8.mss301.propertiesservice.features.roomprice.infrastructure.RoomPriceRepository;
import fptu.semester8.mss301.propertiesservice.features.roomtype.application.RoomTypeService;
import fptu.semester8.mss301.propertiesservice.features.listingintegration.api.dto.ListingRoomSnapshotResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingSnapshotService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final RoomService rooms;
    private final BuildingService buildings;
    private final RoomTypeService roomTypes;
    private final RoomPriceRepository prices;

    public ListingSnapshotService(RoomService rooms, BuildingService buildings, RoomTypeService roomTypes,
            RoomPriceRepository prices) {
        this.rooms = rooms;
        this.buildings = buildings;
        this.roomTypes = roomTypes;
        this.prices = prices;
    }

    @Transactional(readOnly = true)
    public ListingRoomSnapshotResponse getRoomSnapshot(long roomId) {
        var room = rooms.get(roomId);
        var building = buildings.getById(room.getBuildingId());
        var roomType = roomTypes.get(room.getRoomTypeId());
        var price = prices.findCoveringDate(roomId, LocalDate.now(BUSINESS_ZONE))
                .map(current -> current.getPrice()).orElse(room.getBasePrice());
        return new ListingRoomSnapshotResponse(room.getId(), building.getBuildingId(), building.getName(),
                building.getAddress(), building.getWardId(), null, room.getRoomTypeId(), roomType.getName(),
                room.getRoomCode(), room.getMaxCapacity(), room.getStatus(), price,
                building.getLatitude(), building.getLongitude());
    }
}
