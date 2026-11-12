package com.rental.asset.controller;

import com.rental.asset.dto.ApplianceCreateRequest;
import com.rental.asset.dto.ApplianceUpdateRequest;
import com.rental.common.dto.PageResponse;
import com.rental.asset.dto.UploadUrlRequest;
import com.rental.asset.dto.UploadUrlResponse;
import com.rental.asset.dto.WarrantyUpdateRequest;
import com.rental.asset.entity.Appliance;
import com.rental.asset.service.ApplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Tag(name = "Thiết bị & Nội thất")
    @Operation(summary = "Thêm thiết bị/nội thất", description = "Tạo mới thiết bị hoặc nội thất gắn với phòng.")
    public Appliance createAppliance(@Valid @RequestBody ApplianceCreateRequest request) {
        return applianceService.createAppliance(request);
    }

    @GetMapping("/api/v1/appliances")
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Tag(name = "Thiết bị & Nội thất")
    @Operation(summary = "Danh sách thiết bị", description = "Tra cứu phân trang thiết bị, hỗ trợ lọc theo roomId, buildingId và trạng thái.")
    public PageResponse<Appliance> findAppliances(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return applianceService.findAppliances(roomId, buildingId, status, pageNumber, pageSize);
    }

    @GetMapping("/api/v1/appliances/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Tag(name = "Thiết bị & Nội thất")
    @Operation(summary = "Chi tiết thiết bị", description = "Lấy thông tin chi tiết một thiết bị theo ID.")
    public Appliance getAppliance(@PathVariable("id") Long applianceId) {
        return applianceService.getAppliance(applianceId);
    }

    @PutMapping("/api/v1/appliances/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Tag(name = "Thiết bị & Nội thất")
    @Operation(summary = "Cập nhật thông tin thiết bị", description = "Cập nhật tên, mô tả, trạng thái hoặc phòng gán cho thiết bị.")
    public Appliance updateAppliance(
            @PathVariable("id") Long applianceId,
            @Valid @RequestBody ApplianceUpdateRequest request) {
        return applianceService.updateAppliance(applianceId, request);
    }

    @PutMapping("/api/v1/appliances/{id}/warranty")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Tag(name = "Thiết bị & Nội thất")
    @Operation(summary = "Cập nhật thông tin bảo hành", description = "Cập nhật ngày hết hạn bảo hành và thông tin nhà cung cấp cho thiết bị.")
    public Appliance updateWarranty(
            @PathVariable("id") Long applianceId,
            @Valid @RequestBody WarrantyUpdateRequest request) {
        return applianceService.updateWarranty(applianceId, request);
    }

    @DeleteMapping("/api/v1/appliances/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Tag(name = "Thiết bị & Nội thất")
    @Operation(summary = "Xóa thiết bị", description = "Xóa thiết bị theo ID khỏi danh mục quản lý.")
    public void deleteAppliance(@PathVariable("id") Long applianceId) {
        applianceService.deleteAppliance(applianceId);
    }

    @PostMapping("/api/v1/reports/image-upload-url")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Báo hỏng từ khách thuê")
    @Operation(summary = "Tạo presigned URL upload ảnh báo hỏng", description = "Cấp URL upload trực tiếp ảnh hiện trường báo hỏng lên MinIO bucket report-images.")
    public UploadUrlResponse createReportImageUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return applianceService.createReportImageUploadUrl(request);
    }
}
