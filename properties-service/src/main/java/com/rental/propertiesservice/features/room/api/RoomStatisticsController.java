package com.rental.propertiesservice.features.room.api;

import com.rental.common.error.BusinessException;
import com.rental.propertiesservice.features.room.api.dto.RoomStatisticsResponse;
import com.rental.propertiesservice.features.room.application.RoomStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms/statistics")
@Tag(name = "Phòng")
@RequiredArgsConstructor
public class RoomStatisticsController {
    private final RoomStatisticsService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thống kê phòng", description = "Dành cho ADMIN. Tổng số phòng, phòng có hơn 1 người ở và phòng cần cập nhật; lọc tùy chọn theo buildingId, tính theo ngày hiện tại tại Việt Nam.")
    public RoomStatisticsResponse statistics(
            @RequestParam(required = false) Long buildingId) {
        if (buildingId != null && buildingId <= 0) {
            throw new BusinessException("INVALID_BUILDING_ID", "buildingId phải là số dương", HttpStatus.BAD_REQUEST);
        }
        var result = service.summarize(buildingId);
        return new RoomStatisticsResponse(result.totalRooms(), result.operatingRooms(),
                result.roomsNeedingUpdate(), result.businessDate());
    }
}
