package com.rental.asset.controller;

import com.rental.asset.dto.MaintenanceScheduleCreateRequest;
import com.rental.asset.dto.MaintenanceScheduleUpdateRequest;
import com.rental.asset.entity.MaintenanceSchedule;
import com.rental.asset.service.MaintenanceScheduleService;
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
public class MaintenanceScheduleController {

    private final MaintenanceScheduleService maintenanceScheduleService;

    public MaintenanceScheduleController(MaintenanceScheduleService maintenanceScheduleService) {
        this.maintenanceScheduleService = maintenanceScheduleService;
    }

    @PostMapping("/api/v1/maintenance-schedules")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceSchedule createSchedule(@Valid @RequestBody MaintenanceScheduleCreateRequest request) {
        return maintenanceScheduleService.createSchedule(request);
    }

    @GetMapping("/api/v1/maintenance-schedules")
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','ADMIN')")
    public List<MaintenanceSchedule> findSchedules(@RequestParam(required = false) Long buildingId) {
        return maintenanceScheduleService.findSchedules(buildingId);
    }

    @PutMapping("/api/v1/maintenance-schedules/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public MaintenanceSchedule updateSchedule(
            @PathVariable("id") Long scheduleId,
            @Valid @RequestBody MaintenanceScheduleUpdateRequest request) {
        return maintenanceScheduleService.updateSchedule(scheduleId, request);
    }

    @PostMapping("/api/v1/maintenance-schedules/{id}/complete")
    @PreAuthorize("hasAnyRole('KY_THUAT','QUAN_LY','ADMIN')")
    public MaintenanceSchedule completeSchedule(@PathVariable("id") Long scheduleId) {
        return maintenanceScheduleService.completeSchedule(scheduleId);
    }

    @DeleteMapping("/api/v1/maintenance-schedules/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateSchedule(@PathVariable("id") Long scheduleId) {
        maintenanceScheduleService.deactivateSchedule(scheduleId);
    }
}
