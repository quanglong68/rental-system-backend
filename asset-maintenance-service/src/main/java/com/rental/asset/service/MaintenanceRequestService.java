package com.rental.asset.service;

import com.rental.asset.dto.MaintenanceCompletionRequest;
import com.rental.asset.dto.MaintenanceRequestAssignRequest;
import com.rental.common.dto.PageResponse;
import com.rental.asset.entity.MaintenanceRecord;
import com.rental.asset.entity.MaintenanceReport;
import com.rental.asset.entity.MaintenanceRequest;
import com.rental.asset.repository.ApplianceRepository;
import com.rental.asset.repository.MaintenanceRecordRepository;
import com.rental.asset.repository.MaintenanceReportRepository;
import com.rental.asset.repository.MaintenanceRequestRepository;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceRequestService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final MaintenanceReportRepository maintenanceReportRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final ApplianceRepository applianceRepository;
    private final AssetAccessService assetAccessService;
    private final OutboxEventService outboxEventService;
    private final PageRequestFactory pageRequestFactory;

    public MaintenanceRequestService(
            MaintenanceRequestRepository maintenanceRequestRepository,
            MaintenanceReportRepository maintenanceReportRepository,
            MaintenanceRecordRepository maintenanceRecordRepository,
            ApplianceRepository applianceRepository,
            AssetAccessService assetAccessService,
            OutboxEventService outboxEventService,
            PageRequestFactory pageRequestFactory) {
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.maintenanceReportRepository = maintenanceReportRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.applianceRepository = applianceRepository;
        this.assetAccessService = assetAccessService;
        this.outboxEventService = outboxEventService;
        this.pageRequestFactory = pageRequestFactory;
    }

    /** Danh sach phieu (docs 9.6): KY_THUAT chi thay phieu cua minh, quan ly chi trong toa duoc phan cong. */
    public PageResponse<MaintenanceRequest> findRequests(Long buildingId, Long staffId, String status,
            String priority, int pageNumber, int pageSize) {
        PageRequest pageRequest = pageRequestFactory.create(pageNumber, pageSize);
        if (assetAccessService.currentUserHasRole("KY_THUAT")) {
            return PageResponse.from(maintenanceRequestRepository.searchByStaff(
                    assetAccessService.currentUserId(), status, priority, pageRequest));
        }
        if (buildingId != null) {
            assetAccessService.requireBuildingAccess(buildingId);
            return PageResponse.from(maintenanceRequestRepository.search(
                    buildingId, staffId, status, priority, pageRequest));
        }
        if (assetAccessService.isAdmin()) {
            return PageResponse.from(maintenanceRequestRepository.search(
                    null, staffId, status, priority, pageRequest));
        }
        // QUAN_LY khong truyen buildingId -> gop cac toa duoc phan cong (docs 6.2).
        List<Long> allowed = assetAccessService.assignedBuildingIds();
        if (allowed.isEmpty()) {
            return PageResponse.from(Page.empty(pageRequest));
        }
        return PageResponse.from(maintenanceRequestRepository.searchIn(
                allowed, staffId, status, priority, pageRequest));
    }

    public PageResponse<MaintenanceRequest> findCurrentCustomerRequests(int pageNumber, int pageSize) {
        PageRequest pageRequest = pageRequestFactory.create(pageNumber, pageSize);
        List<Long> reportIds = maintenanceReportRepository.findIdsByCustomerId(assetAccessService.currentUserId());
        if (reportIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageRequest));
        }
        return PageResponse.from(maintenanceRequestRepository.findByReportIdIn(reportIds, pageRequest));
    }

    public MaintenanceRequest getRequest(Long requestId) {
        MaintenanceRequest maintenanceRequest = findRequiredRequest(maintenanceRequestRepository.findById(requestId));

        if (assetAccessService.currentUserHasRole("CUSTOMER")) {
            MaintenanceReport report = findRequiredReport(
                    maintenanceReportRepository.findById(maintenanceRequest.getReportId()));
            if (!report.getCustomerId().equals(assetAccessService.currentUserId())) {
                throw new AccessDeniedException("Không có quyền xem phiếu này");
            }
        } else if (assetAccessService.currentUserHasRole("KY_THUAT")) {
            requireAssignedTechnician(maintenanceRequest);
        } else {
            assetAccessService.requireBuildingAccess(maintenanceRequest.getBuildingId());
        }
        return maintenanceRequest;
    }

    @Transactional
    public MaintenanceRequest assignRequest(Long requestId, MaintenanceRequestAssignRequest request) {
        MaintenanceRequest maintenanceRequest = findRequiredRequest(maintenanceRequestRepository.findById(requestId));
        assetAccessService.requireBuildingAccess(maintenanceRequest.getBuildingId());
        if ("COMPLETED".equals(maintenanceRequest.getStatus())) {
            throw invalidStatus("Phiếu đã hoàn tất, không thể phân công lại");
        }
        assetAccessService.requireStaffBelongsToBuilding(request.staffId(), maintenanceRequest.getBuildingId());
        maintenanceRequest.setStaffId(request.staffId());
        maintenanceRequest.setPriority(request.priority());
        maintenanceRequest.setScheduledAt(request.scheduledAt());
        maintenanceRequest.setStatus("ASSIGNED");
        maintenanceRequest = maintenanceRequestRepository.save(maintenanceRequest);

        MaintenanceReport report = findRequiredReport(
                maintenanceReportRepository.findById(maintenanceRequest.getReportId()));
        recordAssignedEvent(maintenanceRequest, report);
        return maintenanceRequest;
    }

    public MaintenanceRequest startRequest(Long requestId) {
        MaintenanceRequest maintenanceRequest = findRequiredRequest(maintenanceRequestRepository.findById(requestId));
        requireAssignedTechnician(maintenanceRequest);
        if (!"ASSIGNED".equals(maintenanceRequest.getStatus())) {
            throw invalidStatus("Phiếu không ở trạng thái được giao");
        }
        maintenanceRequest.setStatus("IN_PROGRESS");
        return maintenanceRequestRepository.save(maintenanceRequest);
    }

    @Transactional
    public MaintenanceRequest completeRequest(Long requestId, MaintenanceCompletionRequest request) {
        MaintenanceRequest maintenanceRequest = findRequiredRequest(maintenanceRequestRepository.findById(requestId));
        if (assetAccessService.currentUserHasRole("KY_THUAT")) {
            requireAssignedTechnician(maintenanceRequest);
        } else {
            assetAccessService.requireBuildingAccess(maintenanceRequest.getBuildingId());
        }
        if (!"IN_PROGRESS".equals(maintenanceRequest.getStatus())) {
            throw invalidStatus("Cần bắt đầu xử lý trước khi hoàn tất");
        }

        MaintenanceRecord maintenanceRecord = new MaintenanceRecord();
        maintenanceRecord.setMaintenanceRequestId(requestId);
        maintenanceRecord.setActionTaken(request.actionTaken());
        maintenanceRecord.setCost(request.cost() == null ? BigDecimal.ZERO : request.cost());
        maintenanceRecord.setCompletedBy(assetAccessService.currentUserId());
        maintenanceRecordRepository.save(maintenanceRecord);

        maintenanceRequest.setStatus("COMPLETED");
        maintenanceRequest = maintenanceRequestRepository.save(maintenanceRequest);

        if (maintenanceRequest.getApplianceId() != null) {
            var appliance = applianceRepository.findById(maintenanceRequest.getApplianceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thiết bị"));
            appliance.setStatus(request.applianceStatus() == null ? "GOOD" : request.applianceStatus());
            applianceRepository.save(appliance);
        }

        MaintenanceReport report = findRequiredReport(
                maintenanceReportRepository.findById(maintenanceRequest.getReportId()));
        outboxEventService.recordEvent(
                "MaintenanceCompleted",
                Map.of(
                        "maintenanceRequestId", requestId,
                        "reportId", maintenanceRequest.getReportId(),
                        "customerId", report.getCustomerId(),
                        "staffId", assetAccessService.currentUserId(),
                        "roomId", maintenanceRequest.getRoomId(),
                        "buildingId", maintenanceRequest.getBuildingId(),
                        "cost", maintenanceRecord.getCost()),
                requestId.toString());
        return maintenanceRequest;
    }

    private void recordAssignedEvent(MaintenanceRequest maintenanceRequest, MaintenanceReport report) {
        outboxEventService.recordEvent(
                "MaintenanceRequestAssigned",
                Map.of(
                        "maintenanceRequestId", maintenanceRequest.getId(),
                        "reportId", maintenanceRequest.getReportId(),
                        "customerId", report.getCustomerId(),
                        "staffId", maintenanceRequest.getStaffId(),
                        "roomId", maintenanceRequest.getRoomId(),
                        "buildingId", maintenanceRequest.getBuildingId(),
                        "priority", maintenanceRequest.getPriority(),
                        "scheduledAt", maintenanceRequest.getScheduledAt()),
                maintenanceRequest.getId().toString());
    }

    private void requireAssignedTechnician(MaintenanceRequest maintenanceRequest) {
        if (maintenanceRequest.getStaffId() == null
                || !maintenanceRequest.getStaffId().equals(assetAccessService.currentUserId())) {
            throw new AccessDeniedException("Phiếu không được giao cho bạn");
        }
    }

    private BusinessException invalidStatus(String message) {
        return new BusinessException("INVALID_STATUS", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private MaintenanceRequest findRequiredRequest(Optional<MaintenanceRequest> requestOptional) {
        return requestOptional.orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu sửa chữa"));
    }

    private MaintenanceReport findRequiredReport(Optional<MaintenanceReport> reportOptional) {
        return reportOptional.orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy báo cáo"));
    }
}
