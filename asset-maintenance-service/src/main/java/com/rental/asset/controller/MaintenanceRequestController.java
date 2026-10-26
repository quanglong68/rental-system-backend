package com.rental.asset.controller;

import com.rental.asset.dto.MaintenanceCompletionRequest;
import com.rental.asset.dto.MaintenanceRequestAssignRequest;
import com.rental.asset.dto.PageResponse;
import com.rental.asset.entity.MaintenanceRequest;
import com.rental.asset.service.MaintenanceRequestService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MaintenanceRequestController {

    private final MaintenanceRequestService maintenanceRequestService;

    public MaintenanceRequestController(MaintenanceRequestService maintenanceRequestService) {
        this.maintenanceRequestService = maintenanceRequestService;
    }

    @GetMapping("/api/v1/maintenance-requests")
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','ADMIN')")
    public PageResponse<MaintenanceRequest> findRequests(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return maintenanceRequestService.findRequests(buildingId, staffId, pageNumber, pageSize);
    }

    @GetMapping("/api/v1/maintenance-requests/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<MaintenanceRequest> findMyRequests() {
        return maintenanceRequestService.findCurrentCustomerRequests();
    }

    @GetMapping("/api/v1/maintenance-requests/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','QUAN_LY','KY_THUAT','ADMIN')")
    public MaintenanceRequest getRequest(@PathVariable("id") Long maintenanceRequestId) {
        return maintenanceRequestService.getRequest(maintenanceRequestId);
    }

    @PutMapping("/api/v1/maintenance-requests/{id}/assign")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public MaintenanceRequest assignRequest(
            @PathVariable("id") Long maintenanceRequestId,
            @Valid @RequestBody MaintenanceRequestAssignRequest request) {
        return maintenanceRequestService.assignRequest(maintenanceRequestId, request);
    }

    @PutMapping("/api/v1/maintenance-requests/{id}/start")
    @PreAuthorize("hasRole('KY_THUAT')")
    public MaintenanceRequest startRequest(@PathVariable("id") Long maintenanceRequestId) {
        return maintenanceRequestService.startRequest(maintenanceRequestId);
    }

    @PostMapping("/api/v1/maintenance-requests/{id}/complete")
    @PreAuthorize("hasAnyRole('KY_THUAT','QUAN_LY','ADMIN')")
    public MaintenanceRequest completeRequest(
            @PathVariable("id") Long maintenanceRequestId,
            @Valid @RequestBody MaintenanceCompletionRequest request) {
        return maintenanceRequestService.completeRequest(maintenanceRequestId, request);
    }
}
