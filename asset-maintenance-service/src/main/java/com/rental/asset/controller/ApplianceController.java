package com.rental.asset.controller;

import com.rental.asset.dto.ApplianceCreateRequest;
import com.rental.asset.dto.ApplianceUpdateRequest;
import com.rental.asset.dto.PageResponse;
import com.rental.asset.dto.UploadUrlRequest;
import com.rental.asset.dto.UploadUrlResponse;
import com.rental.asset.dto.WarrantyUpdateRequest;
import com.rental.asset.entity.Appliance;
import com.rental.asset.service.ApplianceService;
import jakarta.validation.Valid;
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
public class ApplianceController {

    private final ApplianceService applianceService;

    public ApplianceController(ApplianceService applianceService) {
        this.applianceService = applianceService;
    }

    @PostMapping("/api/v1/appliances")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Appliance createAppliance(@Valid @RequestBody ApplianceCreateRequest request) {
        return applianceService.createAppliance(request);
    }

    @GetMapping("/api/v1/appliances")
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','ADMIN')")
    public PageResponse<Appliance> findAppliances(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return applianceService.findAppliances(roomId, buildingId, status, pageNumber, pageSize);
    }

    @GetMapping("/api/v1/appliances/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','ADMIN')")
    public Appliance getAppliance(@PathVariable("id") Long applianceId) {
        return applianceService.getAppliance(applianceId);
    }

    @PutMapping("/api/v1/appliances/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public Appliance updateAppliance(
            @PathVariable("id") Long applianceId,
            @Valid @RequestBody ApplianceUpdateRequest request) {
        return applianceService.updateAppliance(applianceId, request);
    }

    @PutMapping("/api/v1/appliances/{id}/warranty")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public Appliance updateWarranty(
            @PathVariable("id") Long applianceId,
            @Valid @RequestBody WarrantyUpdateRequest request) {
        return applianceService.updateWarranty(applianceId, request);
    }

    @DeleteMapping("/api/v1/appliances/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAppliance(@PathVariable("id") Long applianceId) {
        applianceService.deleteAppliance(applianceId);
    }

    @PostMapping("/api/v1/reports/image-upload-url")
    @PreAuthorize("hasRole('CUSTOMER')")
    public UploadUrlResponse createReportImageUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return applianceService.createReportImageUploadUrl(request);
    }
}
