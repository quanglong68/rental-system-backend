package com.rental.asset.controller;

import com.rental.asset.dto.MaintenanceCompletionRequest;
import com.rental.asset.dto.MaintenanceRequestAssignRequest;
import com.rental.common.dto.PageResponse;
import com.rental.asset.entity.MaintenanceRequest;
import com.rental.asset.service.MaintenanceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Yêu cầu sửa chữa", description = "Phân công kỹ thuật viên, bắt đầu và hoàn tất xử lý sửa chữa")
public class MaintenanceRequestController {

    private final MaintenanceRequestService maintenanceRequestService;

    public MaintenanceRequestController(MaintenanceRequestService maintenanceRequestService) {
        this.maintenanceRequestService = maintenanceRequestService;
    }

    @GetMapping("/api/v1/maintenance-requests")
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Danh sách yêu cầu sửa chữa", description = "Tra cứu phân trang các yêu cầu sửa chữa, lọc theo buildingId, staffId, status và priority.")
    public PageResponse<MaintenanceRequest> findRequests(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return maintenanceRequestService.findRequests(buildingId, staffId, status, priority, pageNumber, pageSize);
    }

    @GetMapping("/api/v1/maintenance-requests/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Danh sách yêu cầu sửa chữa của tôi", description = "Khách thuê xem tiến độ sửa chữa các sự cố thuộc phòng mình.")
    public PageResponse<MaintenanceRequest> findMyRequests(
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize) {
        return maintenanceRequestService.findCurrentCustomerRequests(pageNumber, pageSize);
    }

    @GetMapping("/api/v1/maintenance-requests/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Chi tiết yêu cầu sửa chữa", description = "Xem chi tiết phiếu sửa chữa theo ID.")
    public MaintenanceRequest getRequest(@PathVariable("id") Long maintenanceRequestId) {
        return maintenanceRequestService.getRequest(maintenanceRequestId);
    }

    @PutMapping("/api/v1/maintenance-requests/{id}/assign")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Phân công kỹ thuật viên", description = "Quản lý/Admin gán nhân viên kỹ thuật (KY_THUAT) xử lý yêu cầu sửa chữa.")
    public MaintenanceRequest assignRequest(
            @PathVariable("id") Long maintenanceRequestId,
            @Valid @RequestBody MaintenanceRequestAssignRequest request) {
        return maintenanceRequestService.assignRequest(maintenanceRequestId, request);
    }

    @PutMapping("/api/v1/maintenance-requests/{id}/start")
    @PreAuthorize("hasRole('KY_THUAT')")
    @Operation(summary = "Bắt đầu xử lý sửa chữa", description = "Kỹ thuật viên xác nhận tiếp nhận và bắt đầu sửa chữa.")
    public MaintenanceRequest startRequest(@PathVariable("id") Long maintenanceRequestId) {
        return maintenanceRequestService.startRequest(maintenanceRequestId);
    }

    @PostMapping("/api/v1/maintenance-requests/{id}/complete")
    @PreAuthorize("hasAnyRole('KY_THUAT','QUAN_LY','ADMIN')")
    @Operation(summary = "Hoàn tất sửa chữa", description = "Ghi nhận kết quả hoàn tất, chi phí thực tế (nếu có) và cập nhật trạng thái phiếu sửa chữa.")
    public MaintenanceRequest completeRequest(
            @PathVariable("id") Long maintenanceRequestId,
            @Valid @RequestBody MaintenanceCompletionRequest request) {
        return maintenanceRequestService.completeRequest(maintenanceRequestId, request);
    }
}
