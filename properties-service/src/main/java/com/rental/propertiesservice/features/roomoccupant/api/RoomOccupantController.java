package com.rental.propertiesservice.features.roomoccupant.api;

import com.rental.common.dto.PageResponse;
import com.rental.common.error.BusinessException;
import com.rental.propertiesservice.features.roomoccupant.api.dto.*;
import com.rental.propertiesservice.features.roomoccupant.application.RoomOccupantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    public PageResponse<RoomOccupantResponse> current(@PathVariable @Positive long roomId,
                                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.current(roomId, pageable(page, size)));
    }

    @GetMapping("/{roomId}/occupants/history")
    public PageResponse<RoomOccupantResponse> history(@PathVariable @Positive long roomId,
                                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.history(roomId, pageable(page, size)));
    }

    @GetMapping("/{roomId}/occupants/{occupantId}")
    public RoomOccupantResponse detail(@PathVariable @Positive long roomId,
                                       @PathVariable @Positive long occupantId) {
        return service.detail(roomId, occupantId);
    }

    @GetMapping("/{roomId}/occupancy-summary")
    public OccupancySummaryResponse summary(@PathVariable @Positive long roomId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.summary(roomId, date);
    }

    // Uses the existing property gateway route instead of colliding with user-service.
    @GetMapping("/occupancies/users/{userId}")
    public PageResponse<RoomOccupantResponse> byUser(@PathVariable @Positive long userId,
                                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.byUser(userId, PageRequest.of(validPage(page, size), size)));
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(validPage(page, size), size, Sort.by(Sort.Direction.DESC, "moveInDate", "id"));
    }

    private static int validPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("INVALID_PAGE", "phân trang không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        return page;
    }
}
