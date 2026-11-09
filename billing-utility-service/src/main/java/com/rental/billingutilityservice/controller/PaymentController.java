package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.CreatePaymentRequest;
import com.rental.billingutilityservice.dto.request.CreateVnpayPaymentRequest;
import com.rental.billingutilityservice.dto.response.ConfirmVnpayPaymentResponse;
import com.rental.billingutilityservice.dto.response.PaymentResponse;
import com.rental.billingutilityservice.dto.response.VnpayPaymentUrlResponse;
import com.rental.billingutilityservice.service.PaymentService;
import com.rental.common.security.HeaderUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thanh toan (docs muc 9.5): QUAN_LY ghi nhan tien mat; VNPay Sandbox cho khach hang. */
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

    @PostMapping("/vnpay/create-url")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public VnpayPaymentUrlResponse createVnpayUrl(@Valid @RequestBody CreateVnpayPaymentRequest request,
            @AuthenticationPrincipal HeaderUser principal) {
        return service.createVnpayPaymentUrl(request, principal);
    }

    @RequestMapping(value = "/vnpay/confirm", method = {RequestMethod.GET, RequestMethod.POST})
    public ConfirmVnpayPaymentResponse confirmVnpayPayment(
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestBody(required = false) Map<String, String> bodyParams) {
        Map<String, String> allParams = new HashMap<>();
        if (queryParams != null) {
            allParams.putAll(queryParams);
        }
        if (bodyParams != null) {
            allParams.putAll(bodyParams);
        }
        return service.confirmVnpayPayment(allParams);
    }

    @RequestMapping(value = "/vnpay-ipn", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(service.handleVnpayIpn(params));
    }

    @PostMapping("/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public PaymentResponse simulate(@Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal HeaderUser principal) {
        return service.simulateCustomerPayment(request, principal);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PaymentResponse> list(@RequestParam Long invoiceId,
            @AuthenticationPrincipal HeaderUser principal) {
        return service.listByInvoice(invoiceId, principal);
    }
}
