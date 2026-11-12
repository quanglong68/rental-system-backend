package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.CreateMeterRequest;
import com.rental.billingutilityservice.dto.response.MeterResponse;
import com.rental.billingutilityservice.service.MeterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Dong ho (docs muc 9.5): QUAN_LY dang ky, STAFF xem. */
@RestController
@RequestMapping("/api/v1/meters")
@Tag(name = "Đồng hồ điện nước", description = "Đăng ký và tra cứu đồng hồ đo điện/nước theo phòng hoặc tòa nhà")
@RequiredArgsConstructor
public class MeterController {

    private final MeterService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Đăng ký đồng hồ", description = "Tạo đồng hồ điện/nước gắn cho một phòng cụ thể.")
    public MeterResponse create(@Valid @RequestBody CreateMeterRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Danh sách đồng hồ", description = "Tra cứu danh sách đồng hồ, lọc tùy chọn theo roomId hoặc buildingId.")
    public List<MeterResponse> list(@RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long buildingId) {
        return service.list(roomId, buildingId);
    }
}
