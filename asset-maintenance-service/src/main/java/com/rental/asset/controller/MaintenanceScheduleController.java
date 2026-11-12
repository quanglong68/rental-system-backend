package com.rental.asset.controller;

import com.rental.asset.dto.MaintenanceScheduleCreateRequest;
import com.rental.asset.dto.MaintenanceScheduleUpdateRequest;
import com.rental.asset.entity.MaintenanceSchedule;
import com.rental.asset.service.MaintenanceScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Lịch bảo trì định kỳ", description = "Lập lịch, cập nhật, hoàn tất chu kỳ và ngưng lịch bảo trì")
public class MaintenanceScheduleController {

    private final MaintenanceScheduleService maintenanceScheduleService;

    public MaintenanceScheduleController(MaintenanceScheduleService maintenanceScheduleService) {
        this.maintenanceScheduleService = maintenanceScheduleService;
    }

    @PostMapping("/api/v1/maintenance-schedules")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo lịch bảo trì định kỳ", description = "Thiết lập lịch bảo trì định kỳ cho tòa nhà/thiết bị và kỹ thuật viên phụ trách.")
    public MaintenanceSchedule createSchedule(@Valid @RequestBody MaintenanceScheduleCreateRequest request) {
        return maintenanceScheduleService.createSchedule(request);
    }

    @GetMapping("/api/v1/maintenance-schedules")
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Danh sách lịch bảo trì định kỳ", description = "Tra cứu lịch bảo trì, hỗ trợ lọc theo buildingId hoặc staffId.")
    public List<MaintenanceSchedule> findSchedules(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long staffId) {
        return maintenanceScheduleService.findSchedules(buildingId, staffId);
    }

    @PutMapping("/api/v1/maintenance-schedules/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Cập nhật lịch bảo trì", description = "Chỉnh sửa chu kỳ, ngày đến hạn hoặc người phụ trách lịch bảo trì.")
    public MaintenanceSchedule updateSchedule(
            @PathVariable("id") Long scheduleId,
            @Valid @RequestBody MaintenanceScheduleUpdateRequest request) {
        return maintenanceScheduleService.updateSchedule(scheduleId, request);
    }

    @PostMapping("/api/v1/maintenance-schedules/{id}/complete")
    @PreAuthorize("hasAnyRole('KY_THUAT','QUAN_LY','ADMIN')")
    @Operation(summary = "Hoàn tất kỳ bảo trì", description = "Đánh dấu hoàn tất lần bảo trì hiện tại và tự động tính mốc kỳ kế tiếp.")
    public MaintenanceSchedule completeSchedule(@PathVariable("id") Long scheduleId) {
        return maintenanceScheduleService.completeSchedule(scheduleId);
    }

    @DeleteMapping("/api/v1/maintenance-schedules/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Ngưng lịch bảo trì", description = "Vô hiệu hóa lịch bảo trì định kỳ theo ID.")
    public void deactivateSchedule(@PathVariable("id") Long scheduleId) {
        maintenanceScheduleService.deactivateSchedule(scheduleId);
    }
}
