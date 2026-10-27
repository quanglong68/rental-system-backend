package fptu.semester8.mss301.propertiesservice.features.roomoccupant.api;

import fptu.semester8.mss301.propertiesservice.features.roomoccupant.api.dto.*;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.RoomOccupantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/rooms")
@Tag(name = "Room occupants", description = "Read-only contractual occupancy projection. ADMIN room access; CUSTOMER own history only.")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class RoomOccupantController {

    RoomOccupantService service;

    @GetMapping("/{roomId}/occupants")
    public Page<RoomOccupantResponse> current(
            @PathVariable @Positive long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.current(roomId, pageable(page, size));
    }

    @GetMapping("/{roomId}/occupants/history")
    public Page<RoomOccupantResponse> history(
            @PathVariable @Positive long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(roomId, pageable(page, size));
    }

    @GetMapping("/{roomId}/occupants/{occupantId}")
    public RoomOccupantResponse detail(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long occupantId) {
        return service.detail(roomId, occupantId);
    }

    @GetMapping("/{roomId}/occupancy-summary")
    public OccupancySummaryResponse summary(
            @PathVariable @Positive long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.summary(roomId, date);
    }

    @GetMapping("/occupancies/users/{userId}")
    public Page<RoomOccupantResponse> byUser(
            @PathVariable @Positive long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.byUser(userId, PageRequest.of(validPage(page, size), size));
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(validPage(page, size), size, Sort.by(Sort.Direction.DESC, "moveInDate", "id"));
    }

    private static int validPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("invalid pagination");
        return page;
    }
}
