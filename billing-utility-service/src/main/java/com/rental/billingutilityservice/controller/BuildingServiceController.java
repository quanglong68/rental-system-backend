package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.BuildingServiceRequest;
import com.rental.billingutilityservice.dto.response.BuildingServiceResponse;
import com.rental.billingutilityservice.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Dich vu toa nha (docs muc 9.5): QUAN_LY tao/sua, STAFF xem. */
@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "Dịch vụ tòa nhà", description = "Quản lý danh mục dịch vụ phụ thu theo tòa nhà (giữ xe, vệ sinh, internet...)")
@RequiredArgsConstructor
public class BuildingServiceController {

    private final CatalogService catalog;

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Danh sách dịch vụ tòa nhà", description = "Liệt kê các dịch vụ đang cấu hình, lọc tùy chọn theo buildingId.")
    public List<BuildingServiceResponse> list(@RequestParam(required = false) Long buildingId) {
        return catalog.listServices(buildingId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Tạo dịch vụ tòa nhà", description = "Thêm mới một dịch vụ kèm đơn giá và kiểu tính phí cho tòa nhà.")
    public BuildingServiceResponse create(@Valid @RequestBody BuildingServiceRequest request) {
        return catalog.createService(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Cập nhật dịch vụ tòa nhà", description = "Chỉnh sửa thông tin hoặc đơn giá dịch vụ theo ID.")
    public BuildingServiceResponse update(@PathVariable Long id,
            @Valid @RequestBody BuildingServiceRequest request) {
        return catalog.updateService(id, request);
    }
}
