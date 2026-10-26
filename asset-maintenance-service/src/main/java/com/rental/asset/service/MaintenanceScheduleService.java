package com.rental.asset.service;

import com.rental.asset.dto.MaintenanceScheduleCreateRequest;
import com.rental.asset.dto.MaintenanceScheduleUpdateRequest;
import com.rental.asset.entity.MaintenanceSchedule;
import com.rental.asset.repository.MaintenanceScheduleRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceScheduleService {

    private final MaintenanceScheduleRepository maintenanceScheduleRepository;
    private final AssetAccessService assetAccessService;

    public MaintenanceScheduleService(
            MaintenanceScheduleRepository maintenanceScheduleRepository,
            AssetAccessService assetAccessService) {
        this.maintenanceScheduleRepository = maintenanceScheduleRepository;
        this.assetAccessService = assetAccessService;
    }

    public MaintenanceSchedule createSchedule(MaintenanceScheduleCreateRequest request) {
        assetAccessService.requireStaffAssignment(request.staffId(), request.buildingId());
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setBuildingId(request.buildingId());
        schedule.setStaffId(request.staffId());
        schedule.setTitle(request.title());
        schedule.setDescription(request.description());
        schedule.setIntervalMonths(request.intervalMonths());
        schedule.setNextDate(request.firstDate());
        return maintenanceScheduleRepository.save(schedule);
    }

    public List<MaintenanceSchedule> findSchedules(Long buildingId) {
        if (assetAccessService.currentUserHasRole("KY_THUAT")) {
            return maintenanceScheduleRepository.findByStaffIdAndActiveTrue(assetAccessService.currentUserId());
        }
        if (buildingId == null) {
            return maintenanceScheduleRepository.findAll();
        }
        assetAccessService.requireBuildingAccess(buildingId);
        return maintenanceScheduleRepository.findByBuildingIdAndActiveTrue(buildingId);
    }

    public MaintenanceSchedule updateSchedule(Long scheduleId, MaintenanceScheduleUpdateRequest request) {
        MaintenanceSchedule schedule = findRequired(maintenanceScheduleRepository.findById(scheduleId));
        assetAccessService.requireBuildingAccess(schedule.getBuildingId());
        assetAccessService.requireStaffAssignment(request.staffId(), schedule.getBuildingId());
        schedule.setStaffId(request.staffId());
        schedule.setTitle(request.title());
        schedule.setDescription(request.description());
        schedule.setIntervalMonths(request.intervalMonths());
        schedule.setNextDate(request.nextDate());
        return maintenanceScheduleRepository.save(schedule);
    }

    public MaintenanceSchedule completeSchedule(Long scheduleId) {
        MaintenanceSchedule schedule = findRequired(maintenanceScheduleRepository.findById(scheduleId));
        if (assetAccessService.currentUserHasRole("KY_THUAT")
                && schedule.getStaffId().longValue() != assetAccessService.currentUserId().longValue()) {
            throw new org.springframework.security.access.AccessDeniedException("Lịch không được giao cho bạn");
        }
        schedule.setLastDoneDate(LocalDate.now(ZoneOffset.UTC));
        schedule.setNextDate(schedule.getNextDate().plusMonths(schedule.getIntervalMonths()));
        return maintenanceScheduleRepository.save(schedule);
    }

    public void deactivateSchedule(Long scheduleId) {
        MaintenanceSchedule schedule = findRequired(maintenanceScheduleRepository.findById(scheduleId));
        assetAccessService.requireBuildingAccess(schedule.getBuildingId());
        schedule.setActive(false);
        maintenanceScheduleRepository.save(schedule);
    }

    private MaintenanceSchedule findRequired(Optional<MaintenanceSchedule> scheduleOptional) {
        return scheduleOptional.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
