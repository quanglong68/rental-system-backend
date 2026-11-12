package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.UtilityTypeRequest;
import com.rental.billingutilityservice.dto.response.UtilityTypeResponse;
import com.rental.billingutilityservice.service.UtilityTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Loai tien ich (docs muc 9.5): STAFF xem, ADMIN tao/sua. */
@RestController
@RequestMapping("/api/v1/utility-types")
@Tag(name = "Loại tiện ích", description = "Danh mục điện/nước và đơn giá theo đơn vị tính")
@RequiredArgsConstructor
public class UtilityTypeController {

    private final UtilityTypeService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Danh sách loại tiện ích", description = "Trả về danh sách các loại tiện ích (điện, nước...) và đơn giá hiện hành.")
    public List<UtilityTypeResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo loại tiện ích", description = "ADMIN thêm mới một loại tiện ích kèm đơn giá.")
    public UtilityTypeResponse create(@Valid @RequestBody UtilityTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật loại tiện ích", description = "ADMIN cập nhật tên hoặc đơn giá loại tiện ích theo ID.")
    public UtilityTypeResponse update(@PathVariable Long id, @Valid @RequestBody UtilityTypeRequest request) {
        return service.update(id, request);
    }
}
