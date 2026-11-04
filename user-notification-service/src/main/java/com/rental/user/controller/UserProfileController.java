package com.rental.user.controller;

import com.rental.user.dto.BuildingIdsRequest;
import com.rental.common.dto.PageResponse;
import com.rental.user.dto.UpdateProfileRequest;
import com.rental.user.dto.UpdateStaffRequest;
import com.rental.user.dto.UserProfileResponse;
import com.rental.user.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @Validated @RequestMapping("/api/v1")
public class UserProfileController {
    private final UserProfileService service;
    public UserProfileController(UserProfileService service) { this.service = service; }

    @GetMapping("/users/me") @PreAuthorize("isAuthenticated()")
    public UserProfileResponse me() { return service.me(); }

    @PutMapping("/users/me") @PreAuthorize("isAuthenticated()")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) { return service.updateMe(request); }

    @GetMapping("/users/{id}") @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    public UserProfileResponse customer(@PathVariable Long id) { return service.customer(id); }

    /**
     * Danh sách khách hàng có tìm kiếm, dùng cho màn tạo hợp đồng nháp và thêm người ở
     * để nhân sự chọn khách thay vì gõ customerId thô.
     */
    @GetMapping("/users") @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY','SALE')")
    public PageResponse<UserProfileResponse> customers(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listCustomers(keyword, page, size);
    }

    /**
     * SALE được đọc danh sách nhân sự trong phạm vi tòa nhà mình phụ trách để chọn staffId khi tạo
     * lịch hẹn xem phòng (POST /api/v1/appointments yêu cầu staffId). Service tự giới hạn theo
     * buildingIds của người gọi, nên SALE không xem được nhân sự ngoài phạm vi phân công.
     */
    @GetMapping("/staff") @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY','SALE')")
    public PageResponse<UserProfileResponse> staff(@RequestParam(required = false) Long buildingId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listStaff(buildingId, page, size);
    }

    @PutMapping("/staff/{id}") @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse updateStaff(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest request) {
        return service.updateStaff(id, request);
    }

    @GetMapping("/staff/{id}/buildings") @PreAuthorize("isAuthenticated()")
    public Map<String, Object> staffBuildings(@PathVariable("id") Long staffId) {
        service.assertCanViewBuildings(staffId);
        return Map.of("staffId", staffId, "buildingIds", service.buildingIds(staffId));
    }

    @PutMapping("/staff/{id}/buildings") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> replaceBuildings(@PathVariable("id") Long staffId,
            @Valid @RequestBody BuildingIdsRequest request) {
        service.replaceBuildingIds(staffId, request); return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal/staff/{staffId}/buildings")
    public Map<String, Object> internalStaffBuildings(@PathVariable Long staffId) {
        if (!service.existsStaff(staffId)) {
            throw new com.rental.common.error.BusinessException(com.rental.common.error.ErrorCode.NOT_FOUND,
                    "Không tìm thấy hồ sơ nhân viên");
        }
        return Map.of("staffId", staffId, "buildingIds", service.buildingIds(staffId));
    }
}
