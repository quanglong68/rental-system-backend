package com.rental.asset.service;

import com.rental.asset.dto.MaintenanceScheduleCreateRequest;
import com.rental.asset.dto.MaintenanceScheduleUpdateRequest;
import com.rental.asset.entity.MaintenanceSchedule;
import com.rental.asset.repository.MaintenanceScheduleRepository;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public MaintenanceSchedule createSchedule(MaintenanceScheduleCreateRequest request) {
        assetAccessService.requireBuildingAccess(request.buildingId());
        assetAccessService.requireStaffBelongsToBuilding(request.staffId(), request.buildingId());
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setBuildingId(request.buildingId());
        schedule.setStaffId(request.staffId());
        schedule.setTitle(request.title());
        schedule.setDescription(request.description());
        schedule.setIntervalMonths(request.intervalMonths());
        schedule.setNextDate(request.firstDate());
        return maintenanceScheduleRepository.save(schedule);
    }

    public List<MaintenanceSchedule> findSchedules(Long buildingId, Long staffId) {
        if (assetAccessService.currentUserHasRole("KY_THUAT")) {
            return maintenanceScheduleRepository.findByStaffIdAndActiveTrue(assetAccessService.currentUserId());
        }
        if (buildingId != null) {
            assetAccessService.requireBuildingAccess(buildingId);
            return filterByStaff(maintenanceScheduleRepository.findByBuildingIdAndActiveTrue(buildingId), staffId);
        }
        if (assetAccessService.isAdmin()) {
            return staffId == null
                    ? maintenanceScheduleRepository.findByActiveTrue()
                    : maintenanceScheduleRepository.findByStaffIdAndActiveTrue(staffId);
        }
        // QUAN_LY khong truyen buildingId -> gop cac toa duoc phan cong (docs 6.2).
        List<Long> allowed = assetAccessService.assignedBuildingIds();
        if (allowed.isEmpty()) {
            return List.of();
        }
        return filterByStaff(maintenanceScheduleRepository.findByBuildingIdInAndActiveTrue(allowed), staffId);
    }

    @Transactional
    public MaintenanceSchedule updateSchedule(Long scheduleId, MaintenanceScheduleUpdateRequest request) {
        MaintenanceSchedule schedule = findRequired(maintenanceScheduleRepository.findById(scheduleId));
        assetAccessService.requireBuildingAccess(schedule.getBuildingId());
        assetAccessService.requireStaffBelongsToBuilding(request.staffId(), schedule.getBuildingId());
        schedule.setStaffId(request.staffId());
        schedule.setTitle(request.title());
        schedule.setDescription(request.description());
        schedule.setIntervalMonths(request.intervalMonths());
        schedule.setNextDate(request.nextDate());
        return maintenanceScheduleRepository.save(schedule);
    }

    @Transactional
    public MaintenanceSchedule completeSchedule(Long scheduleId) {
        MaintenanceSchedule schedule = findRequired(maintenanceScheduleRepository.findById(scheduleId));
        if (assetAccessService.currentUserHasRole("KY_THUAT")) {
            if (schedule.getStaffId() == null
                    || !schedule.getStaffId().equals(assetAccessService.currentUserId())) {
                throw new AccessDeniedException("Lịch không được giao cho bạn");
            }
        } else {
            assetAccessService.requireBuildingAccess(schedule.getBuildingId());
        }
        if (!schedule.isActive()) {
            throw new BusinessException("INVALID_STATUS", "Lịch đã ngừng áp dụng",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        schedule.setLastDoneDate(today);
        schedule.setNextDate(schedule.getNextDate().plusMonths(schedule.getIntervalMonths()));
        return maintenanceScheduleRepository.save(schedule);
    }

    @Transactional
    public void deactivateSchedule(Long scheduleId) {
        MaintenanceSchedule schedule = findRequired(maintenanceScheduleRepository.findById(scheduleId));
        assetAccessService.requireBuildingAccess(schedule.getBuildingId());
        schedule.setActive(false);
        maintenanceScheduleRepository.save(schedule);
    }

    private List<MaintenanceSchedule> filterByStaff(List<MaintenanceSchedule> schedules, Long staffId) {
        if (staffId == null) {
            return schedules;
        }
        return schedules.stream().filter(s -> staffId.equals(s.getStaffId())).toList();
    }

    private MaintenanceSchedule findRequired(Optional<MaintenanceSchedule> scheduleOptional) {
        return scheduleOptional.orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy lịch bảo trì"));
    }
}
