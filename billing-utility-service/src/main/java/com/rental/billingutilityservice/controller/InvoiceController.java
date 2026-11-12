package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.GenerateInvoiceRequest;
import com.rental.billingutilityservice.dto.response.GenerateInvoiceResult;
import com.rental.billingutilityservice.dto.response.InvoiceResponse;
import com.rental.common.dto.PageResponse;
import com.rental.billingutilityservice.service.InvoiceService;
import com.rental.common.security.HeaderUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Hoa don (docs muc 9.5): QUAN_LY/ADMIN chot so; STAFF xem theo toa nha; khach xem cua minh. */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Hóa đơn thành viên", description = "Phát hành hóa đơn hàng tháng theo phần chia thành viên và tra cứu hóa đơn cá nhân")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService service;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Phát hành hóa đơn tháng", description = "Chốt tiền phòng, điện nước, dịch vụ và sinh hóa đơn cho các hợp đồng hiệu lực trong kỳ (tháng/năm).")
    public GenerateInvoiceResult generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        return service.generate(request.getMonth(), request.getYear(), request.getBuildingId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Danh sách hóa đơn (Quản lý/Nhân sự)", description = "Tra cứu phân trang hóa đơn thành viên theo buildingId, roomId, month, year và status.")
    public PageResponse<InvoiceResponse> search(@RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long roomId, @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.search(buildingId, roomId, month, year, status, pageable);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "Hóa đơn của tôi (Khách thuê)", description = "Khách thuê xem danh sách hóa đơn cá nhân (gồm hóa đơn cọc và hóa đơn hàng tháng).")
    public PageResponse<InvoiceResponse> mine(@AuthenticationPrincipal HeaderUser principal,
            @RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.mine(Long.valueOf(principal.getUserId()), month, year, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết hóa đơn", description = "Xem chi tiết các khoản mục và trạng thái thanh toán của một hóa đơn theo ID.")
    public InvoiceResponse detail(@PathVariable Long id, @AuthenticationPrincipal HeaderUser principal) {
        return service.detail(id, principal);
    }
}
