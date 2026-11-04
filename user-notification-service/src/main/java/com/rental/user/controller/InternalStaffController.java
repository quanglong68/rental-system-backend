package com.rental.user.controller;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.user.dto.StaffSummaryResponse;
import com.rental.user.service.UserProfileService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feign call #5 (spec 5.2): Contract/Asset → User, {@code GET /internal/staff/{staffId}/buildings}.
 */
@RestController
@RequestMapping("/internal/staff")
@RequiredArgsConstructor
public class InternalStaffController {

    private final UserProfileService service;

    @GetMapping("/{staffId}/buildings")
    public Map<String, Object> internalStaffBuildings(@PathVariable Long staffId) {
        if (!service.existsStaff(staffId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ nhân viên");
        }
        return Map.of("staffId", staffId, "buildingIds", service.buildingIds(staffId));
    }

    /** Danh ba nhan su phu trach toa (chat-service: khach chon sale/quan ly de nhan tin). */
    @GetMapping("/by-building/{buildingId}")
    public List<StaffSummaryResponse> byBuilding(@PathVariable Long buildingId) {
        return service.staffByBuilding(buildingId);
    }

    @GetMapping
    public List<StaffSummaryResponse> summaries(@RequestParam("ids") List<Long> ids) {
        return service.staffSummaries(ids);
    }
}
