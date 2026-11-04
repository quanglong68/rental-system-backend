package com.rental.asset.service;

import com.rental.asset.client.ContractClient;
import com.rental.asset.dto.ApproveReportRequest;
import com.rental.common.dto.PageResponse;
import com.rental.asset.dto.RejectReportRequest;
import com.rental.asset.dto.ReportCreateRequest;
import com.rental.asset.entity.MaintenanceRequest;
import com.rental.asset.entity.MaintenanceReport;
import com.rental.asset.repository.ApplianceRepository;
import com.rental.asset.repository.MaintenanceReportRepository;
import com.rental.asset.repository.MaintenanceRequestRepository;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceReportService {

    private final MaintenanceReportRepository maintenanceReportRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final ApplianceRepository applianceRepository;
    private final ContractClient contractClient;
    private final AssetAccessService assetAccessService;
    private final OutboxEventService outboxEventService;
    private final PageRequestFactory pageRequestFactory;

    public MaintenanceReportService(
            MaintenanceReportRepository maintenanceReportRepository,
            MaintenanceRequestRepository maintenanceRequestRepository,
            ApplianceRepository applianceRepository,
            ContractClient contractClient,
            AssetAccessService assetAccessService,
            OutboxEventService outboxEventService,
            PageRequestFactory pageRequestFactory) {
        this.maintenanceReportRepository = maintenanceReportRepository;
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.applianceRepository = applianceRepository;
        this.contractClient = contractClient;
        this.assetAccessService = assetAccessService;
        this.outboxEventService = outboxEventService;
        this.pageRequestFactory = pageRequestFactory;
    }

    @Transactional
    public MaintenanceReport createReport(ReportCreateRequest request) {
        ContractClient.TenantInfo tenantInfo;
        try {
            tenantInfo = contractClient.getTenantInfo(request.roomId(), assetAccessService.currentUserId());
        } catch (Exception exception) {
            throw new BusinessException(
                    "DEPENDENCY_UNAVAILABLE", "Không thể xác minh hợp đồng thuê phòng", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (tenantInfo == null || tenantInfo.buildingId() == null) {
            throw new BusinessException(
                    "DEPENDENCY_UNAVAILABLE", "Không thể xác minh hợp đồng thuê phòng", HttpStatus.SERVICE_UNAVAILABLE);
        }

        if (!tenantInfo.isTenant()) {
            throw new BusinessException(
                    "NOT_A_TENANT_OF_ROOM", "Bạn không phải người thuê hiện tại của phòng này", HttpStatus.FORBIDDEN);
        }
        if (request.imageUrls() != null && request.imageUrls().size() > 5) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tối đa 5 ảnh");
        }

        MaintenanceReport report = new MaintenanceReport();
        report.setCustomerId(assetAccessService.currentUserId());
        report.setRoomId(request.roomId());
        report.setBuildingId(tenantInfo.buildingId());
        report.setTitle(request.title());
        report.setDescription(request.description());
        if (request.imageUrls() != null) {
            report.setImageUrls(request.imageUrls());
        }

        report = maintenanceReportRepository.save(report);
        outboxEventService.recordEvent(
                "ReportCreated",
                Map.of(
                        "reportId", report.getId(),
                        "customerId", assetAccessService.currentUserId(),
                        "roomId", report.getRoomId(),
                        "buildingId", report.getBuildingId(),
                        "title", report.getTitle()),
                report.getId().toString());
        return report;
    }

    public PageResponse<MaintenanceReport> findCurrentCustomerReports(
            String status, int pageNumber, int pageSize) {
        Long customerId = assetAccessService.currentUserId();
        PageRequest pageRequest = pageRequestFactory.create(pageNumber, pageSize);
        if (status == null) {
            return PageResponse.from(maintenanceReportRepository.findByCustomerIdOrderByCreatedAtDesc(
                    customerId, pageRequest));
        }
        return PageResponse.from(maintenanceReportRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(
                customerId, status, pageRequest));
    }

    public PageResponse<MaintenanceReport> findBuildingReports(
            Long buildingId, String status, int pageNumber, int pageSize) {
        assetAccessService.requireBuildingAccess(buildingId);
        PageRequest pageRequest = pageRequestFactory.create(pageNumber, pageSize);
        if (status == null) {
            return PageResponse.from(maintenanceReportRepository.findByBuildingIdOrderByCreatedAtDesc(
                    buildingId, pageRequest));
        }
        return PageResponse.from(maintenanceReportRepository.findByBuildingIdAndStatusOrderByCreatedAtDesc(
                buildingId, status, pageRequest));
    }

    public MaintenanceReport getReport(Long reportId) {
        MaintenanceReport report = findRequired(maintenanceReportRepository.findById(reportId));
        if (assetAccessService.currentUserHasRole("CUSTOMER")) {
            if (!report.getCustomerId().equals(assetAccessService.currentUserId())) {
                throw new AccessDeniedException("Không có quyền xem báo cáo này");
            }
        } else {
            assetAccessService.requireBuildingAccess(report.getBuildingId());
        }
        return report;
    }

    @Transactional
    public MaintenanceRequest approveReport(Long reportId, ApproveReportRequest request) {
        MaintenanceReport report = findRequired(maintenanceReportRepository.findById(reportId));
        assetAccessService.requireBuildingAccess(report.getBuildingId());
        ensureReportIsPending(report);
        assetAccessService.requireStaffBelongsToBuilding(request.staffId(), report.getBuildingId());
        if (request.applianceId() != null) {
            var appliance = applianceRepository.findById(request.applianceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thiết bị"));
            if (!appliance.getRoomId().equals(report.getRoomId())) {
                throw new BusinessException("APPLIANCE_NOT_IN_ROOM",
                        "Thiết bị không thuộc phòng của báo cáo", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        MaintenanceRequest maintenanceRequest = new MaintenanceRequest();
        maintenanceRequest.setReportId(reportId);
        maintenanceRequest.setApplianceId(request.applianceId());
        maintenanceRequest.setRoomId(report.getRoomId());
        maintenanceRequest.setBuildingId(report.getBuildingId());
        maintenanceRequest.setStaffId(request.staffId());
        maintenanceRequest.setPriority(request.priority() == null ? "MEDIUM" : request.priority());
        maintenanceRequest.setScheduledAt(request.scheduledAt());
        maintenanceRequest.setStatus("ASSIGNED");

        report.setStatus("APPROVED");
        maintenanceReportRepository.save(report);
        maintenanceRequest = maintenanceRequestRepository.save(maintenanceRequest);
        publishAssignedEvent(maintenanceRequest, report);
        return maintenanceRequest;
    }

    @Transactional
    public MaintenanceReport rejectReport(Long reportId, RejectReportRequest request) {
        MaintenanceReport report = findRequired(maintenanceReportRepository.findById(reportId));
        assetAccessService.requireBuildingAccess(report.getBuildingId());
        ensureReportIsPending(report);

        report.setStatus("REJECTED");
        report.setRejectReason(request.reason());
        report = maintenanceReportRepository.save(report);
        outboxEventService.recordEvent(
                "ReportRejected",
                Map.of("reportId", reportId, "customerId", report.getCustomerId(), "reason", request.reason()),
                reportId.toString());
        return report;
    }

    private void publishAssignedEvent(MaintenanceRequest maintenanceRequest, MaintenanceReport report) {
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

    private void ensureReportIsPending(MaintenanceReport report) {
        if (!"PENDING".equals(report.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Báo cáo đã được xử lý",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private MaintenanceReport findRequired(Optional<MaintenanceReport> reportOptional) {
        return reportOptional.orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy báo cáo"));
    }
}
