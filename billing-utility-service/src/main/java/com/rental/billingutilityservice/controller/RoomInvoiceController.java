package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.response.RoomInvoiceResponse;
import com.rental.billingutilityservice.dto.response.RoomInvoiceSummaryResponse;
import com.rental.billingutilityservice.service.RoomInvoiceService;
import com.rental.common.dto.PageResponse;
import com.rental.common.security.HeaderUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Hoa don tong theo phong (docs muc 9.5, 11.4). */
@RestController
@RequestMapping("/api/v1/room-invoices")
@Tag(name = "Hóa đơn tổng theo phòng", description = "Tổng hợp công nợ, tiến độ thanh toán của từng thành viên trong phòng và thống kê doanh thu kỳ")
@RequiredArgsConstructor
public class RoomInvoiceController {

    private final RoomInvoiceService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Danh sách hóa đơn phòng (Quản lý/Nhân sự)", description = "Tra cứu phân trang hóa đơn tổng theo phòng, lọc theo buildingId, month, year và status.")
    public PageResponse<RoomInvoiceResponse> search(@RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.search(buildingId, month, year, status, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Thống kê tổng hợp thu tiền theo kỳ", description = "ADMIN xem tổng phải thu, đã thu và công nợ toàn hệ thống theo tháng/năm.")
    public RoomInvoiceSummaryResponse summary(@RequestParam int month, @RequestParam int year) {
        return service.summary(month, year);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "Hóa đơn phòng của tôi", description = "Khách thuê xem hóa đơn tổng của phòng mình kèm phần chia cho từng thành viên.")
    public PageResponse<RoomInvoiceResponse> mine(@AuthenticationPrincipal HeaderUser principal,
            @RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.mine(Long.valueOf(principal.getUserId()), month, year, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết hóa đơn phòng", description = "Xem chi tiết hóa đơn tổng một phòng theo ID.")
    public RoomInvoiceResponse detail(@PathVariable Long id, @AuthenticationPrincipal HeaderUser principal) {
        return service.detail(id, principal);
    }
}
