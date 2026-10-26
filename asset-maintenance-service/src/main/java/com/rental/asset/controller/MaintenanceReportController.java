package com.rental.asset.controller;

import com.rental.asset.dto.ApproveReportRequest;
import com.rental.asset.dto.PageResponse;
import com.rental.asset.dto.RejectReportRequest;
import com.rental.asset.dto.ReportCreateRequest;
import com.rental.asset.entity.MaintenanceReport;
import com.rental.asset.entity.MaintenanceRequest;
import com.rental.asset.service.MaintenanceReportService;
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
public class MaintenanceReportController {

    private final MaintenanceReportService maintenanceReportService;

    public MaintenanceReportController(MaintenanceReportService maintenanceReportService) {
        this.maintenanceReportService = maintenanceReportService;
    }

    @PostMapping("/api/v1/reports")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceReport createReport(@Valid @RequestBody ReportCreateRequest request) {
        return maintenanceReportService.createReport(request);
    }

    @GetMapping("/api/v1/reports/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<MaintenanceReport> findMyReports(
            @RequestParam(required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return maintenanceReportService.findCurrentCustomerReports(status, pageNumber, pageSize);
    }

    @GetMapping("/api/v1/reports")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public PageResponse<MaintenanceReport> findBuildingReports(
            @RequestParam Long buildingId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return maintenanceReportService.findBuildingReports(buildingId, status, pageNumber, pageSize);
    }

    @GetMapping("/api/v1/reports/{id}")
    @PreAuthorize("isAuthenticated()")
    public MaintenanceReport getReport(@PathVariable("id") Long reportId) {
        return maintenanceReportService.getReport(reportId);
    }

    @PutMapping("/api/v1/reports/{id}/approve")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public MaintenanceRequest approveReport(
            @PathVariable("id") Long reportId,
            @Valid @RequestBody ApproveReportRequest request) {
        return maintenanceReportService.approveReport(reportId, request);
    }

    @PutMapping("/api/v1/reports/{id}/reject")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public MaintenanceReport rejectReport(
            @PathVariable("id") Long reportId,
            @Valid @RequestBody RejectReportRequest request) {
        return maintenanceReportService.rejectReport(reportId, request);
    }
}
