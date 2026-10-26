package com.rental.asset.service;

import com.rental.asset.dto.MaintenanceCompletionRequest;
import com.rental.asset.dto.MaintenanceRequestAssignRequest;
import com.rental.asset.dto.PageResponse;
import com.rental.asset.entity.MaintenanceRecord;
import com.rental.asset.entity.MaintenanceReport;
import com.rental.asset.entity.MaintenanceRequest;
import com.rental.asset.repository.ApplianceRepository;
import com.rental.asset.repository.MaintenanceRecordRepository;
import com.rental.asset.repository.MaintenanceReportRepository;
import com.rental.asset.repository.MaintenanceRequestRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    public PageResponse<MaintenanceRequest> findRequests(
            Long buildingId, Long staffId, int pageNumber, int pageSize) {
        PageRequest pageRequest = pageRequestFactory.create(pageNumber, pageSize);
        if (assetAccessService.currentUserHasRole("KY_THUAT")) {
            return PageResponse.from(maintenanceRequestRepository.findByStaffIdOrderByCreatedAtDesc(
                    assetAccessService.currentUserId(), pageRequest));
        }

        if (buildingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần buildingId");
        }

        assetAccessService.requireBuildingAccess(buildingId);
        return PageResponse.from(maintenanceRequestRepository.findByBuildingIdOrderByCreatedAtDesc(
                buildingId, pageRequest));
    }

    public List<MaintenanceRequest> findCurrentCustomerRequests() {
        List<Long> reportIds = maintenanceReportRepository
                .findByCustomerIdOrderByCreatedAtDesc(assetAccessService.currentUserId())
                .stream()
                .map(MaintenanceReport::getId)
                .toList();
        if (reportIds.isEmpty()) {
            return List.of();
        }
        return maintenanceRequestRepository.findByReportIdIn(reportIds);
    }

    public MaintenanceRequest getRequest(Long requestId) {
        MaintenanceRequest maintenanceRequest = findRequiredRequest(maintenanceRequestRepository.findById(requestId));

        if (assetAccessService.currentUserHasRole("KY_THUAT")
                && maintenanceRequest.getStaffId().longValue() != assetAccessService.currentUserId().longValue()) {
            throw new org.springframework.security.access.AccessDeniedException("Phiếu không được giao cho bạn");
        }
        if (assetAccessService.currentUserHasRole("QUAN_LY")) {
            assetAccessService.requireBuildingAccess(maintenanceRequest.getBuildingId());
        }
        if (assetAccessService.currentUserHasRole("CUSTOMER")) {
            MaintenanceReport report = findRequiredReport(
                    maintenanceReportRepository.findById(maintenanceRequest.getReportId()));
            if (report.getCustomerId().longValue() != assetAccessService.currentUserId().longValue()) {
                throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem phiếu này");
            }
        }
        return maintenanceRequest;
    }

    @Transactional
    public MaintenanceRequest assignRequest(Long requestId, MaintenanceRequestAssignRequest request) {
        MaintenanceRequest maintenanceRequest = findRequiredRequest(maintenanceRequestRepository.findById(requestId));
        assetAccessService.requireStaffAssignment(request.staffId(), maintenanceRequest.getBuildingId());
        maintenanceRequest.setStaffId(request.staffId());
        maintenanceRequest.setPriority(request.priority());
        maintenanceRequest.setScheduledAt(request.scheduledAt());
        maintenanceRequest.setStatus("ASSIGNED");
        maintenanceRequest = maintenanceRequestRepository.save(maintenanceRequest);

        MaintenanceReport report = findRequiredReport(
                maintenanceReportRepository.findById(maintenanceRequest.getReportId()));
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
        return maintenanceRequest;
    }

    public MaintenanceRequest startRequest(Long requestId) {
        MaintenanceRequest maintenanceRequest = findRequiredRequest(maintenanceRequestRepository.findById(requestId));
        if (maintenanceRequest.getStaffId().longValue() != assetAccessService.currentUserId().longValue()) {
            throw new org.springframework.security.access.AccessDeniedException("Phiếu không được giao cho bạn");
        }
        if (!maintenanceRequest.getStatus().equals("ASSIGNED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phiếu không ở trạng thái được giao");
        }
        maintenanceRequest.setStatus("IN_PROGRESS");
        return maintenanceRequestRepository.save(maintenanceRequest);
    }

    @Transactional
    public MaintenanceRequest completeRequest(Long requestId, MaintenanceCompletionRequest request) {
        MaintenanceRequest maintenanceRequest = findRequiredRequest(maintenanceRequestRepository.findById(requestId));
        if (assetAccessService.currentUserHasRole("KY_THUAT")
                && maintenanceRequest.getStaffId().longValue() != assetAccessService.currentUserId().longValue()) {
            throw new org.springframework.security.access.AccessDeniedException("Phiếu không được giao cho bạn");
        }
        if (!maintenanceRequest.getStatus().equals("IN_PROGRESS")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cần bắt đầu xử lý trước khi hoàn tất");
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
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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

    private MaintenanceRequest findRequiredRequest(Optional<MaintenanceRequest> requestOptional) {
        return requestOptional.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private MaintenanceReport findRequiredReport(Optional<MaintenanceReport> reportOptional) {
        return reportOptional.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
