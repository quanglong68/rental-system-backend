package com.rental.asset.controller;

import com.rental.asset.dto.ApproveReportRequest;
import com.rental.common.dto.PageResponse;
import com.rental.asset.dto.RejectReportRequest;
import com.rental.asset.dto.ReportCreateRequest;
import com.rental.asset.entity.MaintenanceReport;
import com.rental.asset.entity.MaintenanceRequest;
import com.rental.asset.service.MaintenanceReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Báo hỏng từ khách thuê", description = "Xin URL upload ảnh, gửi và duyệt/từ chối báo hỏng thiết bị")
public class MaintenanceReportController {

    private final MaintenanceReportService maintenanceReportService;

    public MaintenanceReportController(MaintenanceReportService maintenanceReportService) {
        this.maintenanceReportService = maintenanceReportService;
    }

    @PostMapping("/api/v1/reports")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi báo hỏng sự cố", description = "Khách thuê gửi báo hỏng cho phòng đang ở kèm mô tả và danh sách ảnh đã upload.")
    public MaintenanceReport createReport(@Valid @RequestBody ReportCreateRequest request) {
        return maintenanceReportService.createReport(request);
    }

    @GetMapping("/api/v1/reports/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Danh sách báo hỏng của tôi", description = "Khách thuê xem danh sách báo hỏng đã gửi, lọc tùy chọn theo trạng thái.")
    public PageResponse<MaintenanceReport> findMyReports(
            @RequestParam(required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return maintenanceReportService.findCurrentCustomerReports(status, pageNumber, pageSize);
    }

    @GetMapping("/api/v1/reports")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Danh sách báo hỏng theo tòa nhà", description = "Quản lý/Admin xem danh sách báo hỏng của một tòa nhà, lọc tùy chọn theo trạng thái.")
    public PageResponse<MaintenanceReport> findBuildingReports(
            @RequestParam Long buildingId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return maintenanceReportService.findBuildingReports(buildingId, status, pageNumber, pageSize);
    }

    @GetMapping("/api/v1/reports/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết báo hỏng", description = "Trả về thông tin chi tiết một báo hỏng theo ID.")
    public MaintenanceReport getReport(@PathVariable("id") Long reportId) {
        return maintenanceReportService.getReport(reportId);
    }

    @PutMapping("/api/v1/reports/{id}/approve")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Duyệt báo hỏng & tạo yêu cầu sửa chữa", description = "Chấp nhận báo hỏng và chuyển thành phiếu yêu cầu sửa chữa kỹ thuật.")
    public MaintenanceRequest approveReport(
            @PathVariable("id") Long reportId,
            @Valid @RequestBody ApproveReportRequest request) {
        return maintenanceReportService.approveReport(reportId, request);
    }

    @PutMapping("/api/v1/reports/{id}/reject")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Từ chối báo hỏng", description = "Từ chối báo hỏng kèm lý do.")
    public MaintenanceReport rejectReport(
            @PathVariable("id") Long reportId,
            @Valid @RequestBody RejectReportRequest request) {
        return maintenanceReportService.rejectReport(reportId, request);
    }
}
