package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.CreateDepositVnpayPaymentRequest;
import com.rental.billingutilityservice.dto.request.CreatePaymentRequest;
import com.rental.billingutilityservice.dto.request.CreateVnpayPaymentRequest;
import com.rental.billingutilityservice.dto.response.ConfirmVnpayPaymentResponse;
import com.rental.billingutilityservice.dto.response.PaymentResponse;
import com.rental.billingutilityservice.dto.response.VnpayPaymentUrlResponse;
import com.rental.billingutilityservice.service.PaymentService;
import com.rental.common.security.HeaderUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Thanh toán", description = "Ghi nhận thu tiền mặt, thanh toán VNPay Sandbox (tạo URL, xác nhận Return, Webhook IPN) và lịch sử thanh toán")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Ghi nhận thu tiền mặt (QUAN_LY/ADMIN)", description = "Ghi nhận thanh toán tiền mặt (method=CASH) cho hóa đơn, hỗ trợ thu từng phần (PARTIALLY_PAID) hoặc thu đủ (PAID).")
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return service.create(request);
    }

    @PostMapping("/vnpay/create-url")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo link thanh toán VNPay Sandbox", description = "Ký HMAC-SHA512 và trả về URL chuyển hướng sang cổng thanh toán VNPay Sandbox cho số tiền còn nợ (hoặc một phần).")
    public VnpayPaymentUrlResponse createVnpayUrl(@Valid @RequestBody CreateVnpayPaymentRequest request,
            @AuthenticationPrincipal HeaderUser principal) {
        return service.createVnpayPaymentUrl(request, principal);
    }

    @PostMapping("/vnpay/deposit/create-url")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo link thanh toán tiền cọc hợp đồng qua VNPay Sandbox", description = "Ký HMAC-SHA512 và trả về URL chuyển hướng sang cổng VNPay Sandbox cho khoản tiền đặt cọc giữ phòng của hợp đồng.")
    public VnpayPaymentUrlResponse createDepositVnpayUrl(@Valid @RequestBody CreateDepositVnpayPaymentRequest request,
            @AuthenticationPrincipal HeaderUser principal) {
        return service.createDepositVnpayPaymentUrl(request, principal);
    }

    @RequestMapping(value = "/vnpay/confirm", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Xác nhận kết quả VNPay Return URL", description = "Frontend gửi toàn bộ query params sau khi VNPay redirect về để xác thực chữ ký HMAC-SHA512 và cập nhật hóa đơn (idempotent).")
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
    @Operation(summary = "Webhook IPN Server-to-Server từ VNPay", description = "Endpoint công khai nhận callback ngầm từ máy chủ VNPay (khi chạy qua domain public/ngrok), trả về RspCode 00/01/02/04/97.")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(service.handleVnpayIpn(params));
    }

    @PostMapping("/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Giả lập thanh toán chuyển khoản VietQR", description = "Endpoint hỗ trợ test/giả lập khách thanh toán chuyển khoản trực tiếp trên hóa đơn.")
    public PaymentResponse simulate(@Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal HeaderUser principal) {
        return service.simulateCustomerPayment(request, principal);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử các lần thanh toán theo hóa đơn", description = "Liệt kê tất cả giao dịch thanh toán (CASH, VNPAY, BANK_TRANSFER) thuộc một invoiceId.")
    public List<PaymentResponse> list(@RequestParam Long invoiceId,
            @AuthenticationPrincipal HeaderUser principal) {
        return service.listByInvoice(invoiceId, principal);
    }
}
