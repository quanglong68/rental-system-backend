package fptu.semester8.mss301.propertiesservice.features.room.api;

import fptu.semester8.mss301.propertiesservice.features.room.api.dto.RoomStatisticsResponse;
import fptu.semester8.mss301.propertiesservice.features.room.application.RoomStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms/statistics")
@Tag(name = "Phòng")
@RequiredArgsConstructor
public class RoomStatisticsController {
    private final RoomStatisticsService service;

    @GetMapping
    @Operation(summary = "Thống kê phòng",
            description = "Dành cho ADMIN. Tổng số phòng, phòng có hơn 1 người ở và phòng cần cập nhật; " +
                    "lọc tùy chọn theo buildingId, tính theo ngày hiện tại tại Việt Nam.")
    public RoomStatisticsResponse statistics(@RequestParam(required = false) Long buildingId) {
        if (buildingId != null && buildingId <= 0) {
            throw new IllegalArgumentException("buildingId must be positive");
        }
        var result = service.summarize(buildingId);
        return new RoomStatisticsResponse(result.totalRooms(), result.operatingRooms(),
                result.roomsNeedingUpdate(), result.businessDate());
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalidBuildingId() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad request");
        problem.setDetail("buildingId must be a positive integer within the Long range");
        return problem;
    }
}
