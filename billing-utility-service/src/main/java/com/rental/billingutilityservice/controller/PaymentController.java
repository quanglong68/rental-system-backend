package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.CreatePaymentRequest;
import com.rental.billingutilityservice.dto.response.PaymentResponse;
import com.rental.billingutilityservice.service.PaymentService;
import com.rental.common.security.HeaderUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Thanh toan (docs muc 9.5): QUAN_LY ghi nhan tien mat; khach/staff xem theo hoa don. */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PaymentResponse> list(@RequestParam Long invoiceId,
            @AuthenticationPrincipal HeaderUser principal) {
        return service.listByInvoice(invoiceId, principal);
    }
}
