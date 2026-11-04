package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.response.RoomInvoiceResponse;
import com.rental.billingutilityservice.dto.response.RoomInvoiceSummaryResponse;
import com.rental.billingutilityservice.service.RoomInvoiceService;
import com.rental.common.dto.PageResponse;
import com.rental.common.security.HeaderUser;
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
@RequiredArgsConstructor
public class RoomInvoiceController {

    private final RoomInvoiceService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    public PageResponse<RoomInvoiceResponse> search(@RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.search(buildingId, month, year, status, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public RoomInvoiceSummaryResponse summary(@RequestParam int month, @RequestParam int year) {
        return service.summary(month, year);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public PageResponse<RoomInvoiceResponse> mine(@AuthenticationPrincipal HeaderUser principal,
            @RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.mine(Long.valueOf(principal.getUserId()), month, year, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public RoomInvoiceResponse detail(@PathVariable Long id, @AuthenticationPrincipal HeaderUser principal) {
        return service.detail(id, principal);
    }
}
