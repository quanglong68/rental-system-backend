package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.GenerateInvoiceRequest;
import com.rental.billingutilityservice.dto.response.GenerateInvoiceResult;
import com.rental.billingutilityservice.dto.response.InvoiceResponse;
import com.rental.billingutilityservice.dto.response.PageResponse;
import com.rental.billingutilityservice.service.InvoiceService;
import com.rental.common.security.HeaderUser;
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
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService service;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public GenerateInvoiceResult generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        return service.generate(request.getMonth(), request.getYear(), request.getBuildingId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    public PageResponse<InvoiceResponse> search(@RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long roomId, @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.search(buildingId, roomId, month, year, status, pageable);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public PageResponse<InvoiceResponse> mine(@AuthenticationPrincipal HeaderUser principal,
            @RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.mine(Long.valueOf(principal.getUserId()), month, year, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public InvoiceResponse detail(@PathVariable Long id, @AuthenticationPrincipal HeaderUser principal) {
        return service.detail(id, principal);
    }
}
