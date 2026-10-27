package fptu.semester8.mss301.propertiesservice.features.room.api;

import fptu.semester8.mss301.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Existing Asset consumer contract; never exposed through Gateway. */
@RestController
@RequestMapping("/internal/rooms")
public class InternalRoomController {
    private final RoomRepository rooms;

    public InternalRoomController(RoomRepository rooms) {
        this.rooms = rooms;
    }

    public record RoomInfo(Long id, Long buildingId) {}

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomInfo> get(@PathVariable long roomId) {
        return rooms.findById(roomId)
                .map(room -> ResponseEntity.ok(new RoomInfo(room.getId(), room.getBuildingId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
