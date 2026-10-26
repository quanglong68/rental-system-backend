package fptu.semester8.mss301.propertiesservice.features.room.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.apiresponse.exception.BadRequestException;
import fptu.semester8.mss301.propertiesservice.features.room.api.dto.RoomStatisticsResponse;
import fptu.semester8.mss301.propertiesservice.features.room.application.RoomStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms/statistics")
@Tag(name = "Phòng")
@RequiredArgsConstructor
public class RoomStatisticsController {
    private final RoomStatisticsService service;

    @GetMapping
    @Operation(summary = "Thống kê phòng", description = "Dành cho ADMIN. Tổng số phòng, phòng có hơn 1 người ở và phòng cần cập nhật; lọc tùy chọn theo buildingId, tính theo ngày hiện tại tại Việt Nam.")
    public ApiResponse<RoomStatisticsResponse> statistics(
            @RequestParam(required = false) Long buildingId) {
        if (buildingId != null && buildingId <= 0) {
            throw new BadRequestException("buildingId must be positive", "INVALID_BUILDING_ID");
        }
        var result = service.summarize(buildingId);
        return ApiResponse.success(new RoomStatisticsResponse(result.totalRooms(), result.operatingRooms(),
                result.roomsNeedingUpdate(), result.businessDate()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> invalidBuildingId() {
        return ApiResponse.error(400, "buildingId must be a positive integer within the Long range");
    }
}
