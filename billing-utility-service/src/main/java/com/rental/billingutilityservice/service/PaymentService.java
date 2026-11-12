package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.ContractClient;
import com.rental.billingutilityservice.client.ContractDepositInfoResponse;
import com.rental.billingutilityservice.client.MarkContractDepositPaidRequest;
import com.rental.billingutilityservice.dto.request.CreateDepositVnpayPaymentRequest;
import com.rental.billingutilityservice.dto.request.CreatePaymentRequest;
import com.rental.billingutilityservice.dto.request.CreateVnpayPaymentRequest;
import com.rental.billingutilityservice.dto.response.ConfirmVnpayPaymentResponse;
import com.rental.billingutilityservice.dto.response.PaymentResponse;
import com.rental.billingutilityservice.dto.response.VnpayPaymentUrlResponse;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.Payment;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.outbox.BillingEventPublisher;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.PaymentRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.billingutilityservice.vnpay.VnpayClient;
import com.rental.billingutilityservice.vnpay.VnpayProperties;
import com.rental.billingutilityservice.vnpay.VnpaySigner;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.NotFoundException;
import com.rental.common.security.HeaderUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Thanh toan (docs muc 9.5): staff ghi nhan tien mat tung nguoi; tich hop VNPay Sandbox cho khach hang.
 * Hoa don chuyen UNPAID → PARTIALLY_PAID → PAID theo tong da tra; khong cho tra qua so con no
 * (422 AMOUNT_EXCEEDS_REMAINING) hoac khi da PAID (422 INVOICE_ALREADY_PAID).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final RoomInvoiceService roomInvoices;
    private final BillingEventPublisher events;
    private final AuthSupport auth;
    private final ContractClient contractClient;
    private final VnpayClient vnpayClient;
    private final VnpayProperties vnpayProperties;
    private final VnpaySigner vnpaySigner;

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        Invoice invoice = invoices.findById(request.getInvoiceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy hóa đơn"));
        auth.requireBuildingScope(invoice.getBuildingId());
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessException("INVOICE_ALREADY_PAID", "Hóa đơn đã thanh toán đủ",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        BigDecimal remaining = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        if (request.getAmountPaid().compareTo(remaining) > 0) {
            throw new BusinessException("AMOUNT_EXCEEDS_REMAINING", "Số tiền vượt quá số còn nợ",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmountPaid(request.getAmountPaid());
        payment.setMethod("CASH");
        payment.setPaidAt(request.getPaidAt() == null ? Instant.now() : request.getPaidAt());
        payment.setNote(request.getNote());
        payment.setRecordedBy(auth.currentUserId());
        Payment saved = payments.save(payment);
        BigDecimal paidTotal = invoice.getPaidAmount().add(request.getAmountPaid());
        invoice.setPaidAmount(paidTotal);
        if (paidTotal.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (invoice.getStatus() != InvoiceStatus.OVERDUE) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        // OVERDUE tra thieu van giu OVERDUE (docs 9.5: overdue chi set 1 lan, khong dao ve PARTIALLY_PAID).
        invoices.save(invoice);
        roomInvoices.recomputeForShare(invoice);
        events.publishPaymentReceived(saved.getId(), invoice.getId(), invoice.getCustomerId(),
                saved.getAmountPaid(), invoice.getStatus().name());
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse simulateCustomerPayment(CreatePaymentRequest request, com.rental.common.security.HeaderUser principal) {
        Invoice invoice = invoices.findById(request.getInvoiceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy hóa đơn"));

        if ("CUSTOMER".equalsIgnoreCase(principal.getUserType())) {
            if (!String.valueOf(invoice.getCustomerId()).equals(principal.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Không phải hóa đơn của bạn");
            }
        } else if (!"ADMIN".equalsIgnoreCase(principal.getUserType()) && !principal.getRoles().contains("ADMIN")) {
            auth.requireBuildingScope(invoice.getBuildingId());
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessException("INVOICE_ALREADY_PAID", "Hóa đơn đã thanh toán đủ",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        BigDecimal remaining = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        BigDecimal payAmount = request.getAmountPaid() != null && request.getAmountPaid().compareTo(BigDecimal.ZERO) > 0
                ? request.getAmountPaid() : remaining;
        if (payAmount.compareTo(remaining) > 0) {
            payAmount = remaining;
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmountPaid(payAmount);
        payment.setMethod("BANK_TRANSFER");
        payment.setPaidAt(request.getPaidAt() == null ? Instant.now() : request.getPaidAt());
        payment.setNote(request.getNote() != null ? request.getNote() : "Giả lập thanh toán VietQR thành công");
        payment.setRecordedBy(Long.valueOf(principal.getUserId()));
        Payment saved = payments.save(payment);

        BigDecimal paidTotal = invoice.getPaidAmount().add(payAmount);
        invoice.setPaidAmount(paidTotal);
        if (paidTotal.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (invoice.getStatus() != InvoiceStatus.OVERDUE) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoices.save(invoice);
        roomInvoices.recomputeForShare(invoice);
        events.publishPaymentReceived(saved.getId(), invoice.getId(), invoice.getCustomerId(),
                saved.getAmountPaid(), invoice.getStatus().name());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByInvoice(Long invoiceId, com.rental.common.security.HeaderUser principal) {
        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy hóa đơn"));
        boolean admin = "ADMIN".equalsIgnoreCase(principal.getUserType())
                || principal.getRoles().contains("ADMIN");
        boolean owner = String.valueOf(invoice.getCustomerId()).equals(principal.getUserId());
        if (!admin && !owner) {
            if ("CUSTOMER".equalsIgnoreCase(principal.getUserType())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Không phải hóa đơn của bạn");
            }
            auth.requireBuildingScope(invoice.getBuildingId());
        }
        return payments.findByInvoice_IdOrderByPaidAtAsc(invoiceId).stream().map(this::toResponse).toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder().id(payment.getId()).invoiceId(payment.getInvoice().getId())
                .amountPaid(payment.getAmountPaid()).method(payment.getMethod()).paidAt(payment.getPaidAt())
                .note(payment.getNote()).recordedBy(payment.getRecordedBy()).txnRef(payment.getTxnRef()).build();
    }

    /**
     * Tạo URL chuyển hướng thanh toán VNPay Sandbox.
     */
    @Transactional(readOnly = true)
    public VnpayPaymentUrlResponse createVnpayPaymentUrl(CreateVnpayPaymentRequest request, HeaderUser principal) {
        Invoice invoice = invoices.findById(request.getInvoiceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy hóa đơn"));

        authorizeTargetCustomer(invoice.getCustomerId(), invoice.getBuildingId(), principal, "Không phải hóa đơn của bạn");

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessException("INVOICE_ALREADY_PAID", "Hóa đơn đã được thanh toán đủ",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        BigDecimal remaining = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        BigDecimal payAmount = request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0
                ? request.getAmount() : remaining;
        if (payAmount.compareTo(remaining) > 0) {
            payAmount = remaining;
        }
        if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVOICE_ALREADY_PAID", "Hóa đơn không còn dư nợ",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String returnUrl = resolveReturnUrl(request.getReturnUrl());
        String txnRef = "INV" + invoice.getId() + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String orderInfo = "Thanh toan tien phong HD " + invoice.getId();
        long amountVndCents = payAmount.multiply(BigDecimal.valueOf(100)).longValueExact();

        String paymentUrl = vnpayClient.buildPaymentUrl(txnRef, amountVndCents, orderInfo, null, returnUrl);
        log.info("Created VNPay Sandbox payment URL for invoice {}, amount {}, txnRef {}", invoice.getId(), payAmount, txnRef);

        return VnpayPaymentUrlResponse.builder()
                .paymentUrl(paymentUrl)
                .txnRef(txnRef)
                .invoiceId(invoice.getId())
                .amount(payAmount)
                .build();
    }

    /**
     * Tạo URL chuyển hướng thanh toán tiền đặt cọc giữ phòng của hợp đồng qua VNPay Sandbox.
     */
    public VnpayPaymentUrlResponse createDepositVnpayPaymentUrl(CreateDepositVnpayPaymentRequest request, HeaderUser principal) {
        ContractDepositInfoResponse info = executeContractCall(
                () -> contractClient.getContractDepositInfo(request.getContractId()));
        if (info == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy hợp đồng");
        }

        authorizeTargetCustomer(info.getCustomerId(), info.getBuildingId(), principal, "Không phải hợp đồng của bạn");

        if (info.isDepositPaid()) {
            throw new BusinessException("DEPOSIT_ALREADY_PAID", "Tiền đặt cọc hợp đồng đã được thanh toán",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("ENDED".equalsIgnoreCase(info.getStatus()) || "TERMINATED".equalsIgnoreCase(info.getStatus())) {
            throw new BusinessException("INVALID_CONTRACT_STATUS", "Hợp đồng không còn hiệu lực để thanh toán cọc",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (info.getDepositAmount() == null || info.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("NO_DEPOSIT_REQUIRED", "Hợp đồng này không yêu cầu tiền đặt cọc",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String returnUrl = resolveReturnUrl(request.getReturnUrl());
        String txnRef = "DEP" + info.getContractId() + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String orderInfo = "Dat coc hop dong HD " + info.getContractId();
        long amountVndCents = info.getDepositAmount().multiply(BigDecimal.valueOf(100)).longValueExact();

        String paymentUrl = vnpayClient.buildPaymentUrl(txnRef, amountVndCents, orderInfo, null, returnUrl);
        log.info("Created VNPay Sandbox deposit URL for contract {}, amount {}, txnRef {}",
                info.getContractId(), info.getDepositAmount(), txnRef);

        return VnpayPaymentUrlResponse.builder()
                .paymentUrl(paymentUrl)
                .txnRef(txnRef)
                .contractId(info.getContractId())
                .amount(info.getDepositAmount())
                .build();
    }

    private void authorizeTargetCustomer(Long targetCustomerId, Long buildingId, HeaderUser principal, String forbiddenMsg) {
        if (principal == null) {
            return;
        }
        if ("CUSTOMER".equalsIgnoreCase(principal.getUserType())) {
            if (!String.valueOf(targetCustomerId).equals(principal.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, forbiddenMsg);
            }
        } else if (!"ADMIN".equalsIgnoreCase(principal.getUserType()) && !principal.getRoles().contains("ADMIN")) {
            auth.requireBuildingScope(buildingId);
        }
    }

    private String resolveReturnUrl(String returnUrl) {
        if (returnUrl != null && !returnUrl.isBlank()) {
            if (!vnpayProperties.isReturnUrlAllowed(returnUrl)) {
                throw new BusinessException("INVALID_RETURN_URL", "Return URL không nằm trong whitelist cho phép",
                        HttpStatus.BAD_REQUEST);
            }
            return returnUrl.trim();
        }
        return vnpayProperties.getDefaultReturnUrl();
    }

    /**
     * Xác nhận thanh toán VNPay sau khi khách hàng redirect về Frontend.
     * Xác thực chữ ký HMAC-SHA512 và cập nhật hóa đơn hoặc tiền cọc hợp đồng nếu hợp lệ (idempotent, thread-safe).
     */
    @Transactional
    public ConfirmVnpayPaymentResponse confirmVnpayPayment(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Thiếu mã tham chiếu giao dịch vnp_TxnRef")
                    .build();
        }

        if (!vnpaySigner.verify(params, vnpayProperties.getHashSecret())) {
            log.warn("VNPay confirm rejected: invalid signature, txnRef={}", txnRef);
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Chữ ký VNPay không hợp lệ")
                    .txnRef(txnRef)
                    .build();
        }

        String responseCode = params.get("vnp_ResponseCode");
        if (!"00".equals(responseCode)) {
            log.warn("VNPay payment not completed or cancelled, code={}, txnRef={}", responseCode, txnRef);
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Giao dịch không thành công hoặc người dùng đã hủy (Mã VNPay: " + responseCode + ")")
                    .txnRef(txnRef)
                    .build();
        }

        long receivedVnd = parseVnpayAmountVnd(params.get("vnp_Amount"));
        if (receivedVnd <= 0) {
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Số tiền giao dịch VNPay không hợp lệ")
                    .txnRef(txnRef)
                    .build();
        }

        if (txnRef.startsWith("DEP")) {
            return confirmDepositVnpay(txnRef, receivedVnd);
        }

        Long invoiceId = parseInvoiceIdFromTxnRef(txnRef);
        if (invoiceId == null) {
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Không thể nhận diện mã hóa đơn từ txnRef " + txnRef)
                    .txnRef(txnRef)
                    .build();
        }

        // Khóa dòng hóa đơn trước khi kiểm tra idempotency để mọi request đồng thời
        // (vd: React StrictMode hoặc IPN + Return đến cùng lúc) tuần tự hóa sạch sẽ,
        // tránh đụng unique constraint và làm hỏng Hibernate Session.
        Invoice invoice = invoices.findByIdForUpdate(invoiceId).orElse(null);
        if (invoice == null) {
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Hóa đơn #" + invoiceId + " không tồn tại")
                    .txnRef(txnRef)
                    .build();
        }

        Optional<Payment> existing = payments.findByTxnRef(txnRef);
        if (existing.isPresent()) {
            Payment p = existing.get();
            log.info("VNPay payment {} already confirmed previously", txnRef);
            return ConfirmVnpayPaymentResponse.builder()
                    .success(true)
                    .message("Giao dịch đã được ghi nhận thành công trước đó")
                    .invoiceId(invoice.getId())
                    .amount(p.getAmountPaid())
                    .txnRef(txnRef)
                    .build();
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            log.warn("VNPay confirm for already PAID invoice {}, txnRef={}", invoice.getId(), txnRef);
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Hóa đơn #" + invoice.getId() + " đã được thanh toán đủ trước đó")
                    .invoiceId(invoice.getId())
                    .txnRef(txnRef)
                    .build();
        }

        BigDecimal payAmount = BigDecimal.valueOf(receivedVnd);
        String bank = params.getOrDefault("vnp_BankCode", "");
        String transNo = params.getOrDefault("vnp_TransactionNo", "");
        String note = "Thanh toán VNPay Sandbox: GD #" + transNo + ", Ngân hàng: " + bank;
        recordVnpayPayment(invoice, txnRef, payAmount, note);

        log.info("VNPay payment confirmed successfully for invoice {}, txnRef {}, amount {}",
                invoice.getId(), txnRef, payAmount);
        return ConfirmVnpayPaymentResponse.builder()
                .success(true)
                .message("Thanh toán hóa đơn thành công!")
                .invoiceId(invoice.getId())
                .amount(payAmount)
                .txnRef(txnRef)
                .build();
    }

    private ConfirmVnpayPaymentResponse confirmDepositVnpay(String txnRef, long receivedVnd) {
        Long contractId = parsePrefixedIdFromTxnRef(txnRef, "DEP");
        if (contractId == null) {
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Không thể nhận diện mã hợp đồng từ txnRef " + txnRef)
                    .txnRef(txnRef)
                    .build();
        }

        ContractDepositInfoResponse info;
        try {
            info = executeContractCall(() -> contractClient.getContractDepositInfo(contractId));
        } catch (NotFoundException ex) {
            info = null;
        }
        if (info == null) {
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Hợp đồng #" + contractId + " không tồn tại")
                    .txnRef(txnRef)
                    .build();
        }

        if (info.isDepositPaid()) {
            if (txnRef.equals(info.getDepositTxnRef())) {
                log.info("VNPay deposit {} already confirmed previously", txnRef);
                return ConfirmVnpayPaymentResponse.builder()
                        .success(true)
                        .message("Giao dịch đặt cọc đã được ghi nhận thành công trước đó")
                        .contractId(contractId)
                        .amount(info.getDepositAmount())
                        .txnRef(txnRef)
                        .build();
            }
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Tiền cọc hợp đồng #" + contractId + " đã được thanh toán trước đó")
                    .contractId(contractId)
                    .txnRef(txnRef)
                    .build();
        }

        BigDecimal payAmount = BigDecimal.valueOf(receivedVnd);
        if (info.getDepositAmount() == null || payAmount.compareTo(info.getDepositAmount()) != 0) {
            return ConfirmVnpayPaymentResponse.builder()
                    .success(false)
                    .message("Số tiền giao dịch VNPay không khớp với tiền đặt cọc hợp đồng")
                    .contractId(contractId)
                    .txnRef(txnRef)
                    .build();
        }

        executeContractCall(() -> contractClient.markContractDepositPaid(
                contractId, new MarkContractDepositPaidRequest(txnRef, payAmount, "VNPAY")));
        log.info("VNPay deposit confirmed successfully for contract {}, txnRef {}, amount {}",
                contractId, txnRef, payAmount);
        return ConfirmVnpayPaymentResponse.builder()
                .success(true)
                .message("Thanh toán tiền đặt cọc hợp đồng thành công!")
                .contractId(contractId)
                .amount(payAmount)
                .txnRef(txnRef)
                .build();
    }

    /**
     * Webhook IPN từ VNPay server-to-server chuẩn response code 00/01/02/04/97.
     */
    @Transactional
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        if (!vnpaySigner.verify(params, vnpayProperties.getHashSecret())) {
            log.warn("VNPay IPN rejected: invalid signature, txnRef={}", txnRef);
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        }

        long receivedVnd = parseVnpayAmountVnd(params.get("vnp_Amount"));
        if (receivedVnd <= 0) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }

        if (txnRef != null && txnRef.startsWith("DEP")) {
            return handleDepositVnpayIpn(txnRef, receivedVnd, params);
        }

        Long invoiceId = parseInvoiceIdFromTxnRef(txnRef);
        Invoice invoice = invoiceId != null ? invoices.findByIdForUpdate(invoiceId).orElse(null) : null;
        if (invoice == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        if (payments.existsByTxnRef(txnRef) || invoice.getStatus() == InvoiceStatus.PAID) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }

        if ("00".equals(params.get("vnp_ResponseCode"))) {
            BigDecimal payAmount = BigDecimal.valueOf(receivedVnd);
            String bank = params.getOrDefault("vnp_BankCode", "");
            String transNo = params.getOrDefault("vnp_TransactionNo", "");
            String note = "VNPay IPN: GD #" + transNo + ", Ngân hàng: " + bank;
            recordVnpayPayment(invoice, txnRef, payAmount, note);
            log.info("VNPay IPN processed successfully for invoice {}, txnRef {}", invoice.getId(), txnRef);
        }

        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    private Map<String, String> handleDepositVnpayIpn(String txnRef, long receivedVnd, Map<String, String> params) {
        Long contractId = parsePrefixedIdFromTxnRef(txnRef, "DEP");
        ContractDepositInfoResponse info = null;
        if (contractId != null) {
            try {
                info = executeContractCall(() -> contractClient.getContractDepositInfo(contractId));
            } catch (NotFoundException ex) {
                info = null;
            }
        }
        if (info == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }
        if (info.getDepositAmount() == null || BigDecimal.valueOf(receivedVnd).compareTo(info.getDepositAmount()) != 0) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }
        if (info.isDepositPaid()) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }
        if ("00".equals(params.get("vnp_ResponseCode"))) {
            executeContractCall(() -> contractClient.markContractDepositPaid(
                    contractId, new MarkContractDepositPaidRequest(txnRef, BigDecimal.valueOf(receivedVnd), "VNPAY")));
            log.info("VNPay IPN processed deposit successfully for contract {}, txnRef {}", contractId, txnRef);
        }
        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    /**
     * Spring Cloud OpenFeign wraps 4xx/BusinessException/NotFoundException inside
     * NoFallbackAvailableException when circuitbreaker is enabled without a fallback.
     * Unwrap so GlobalExceptionHandler & NotFoundException catches handle it cleanly.
     */
    private <T> T executeContractCall(Supplier<T> call) {
        try {
            return call.get();
        } catch (NoFallbackAvailableException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw ex;
        }
    }

    private void recordVnpayPayment(Invoice invoice, String txnRef, BigDecimal payAmount, String note) {
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmountPaid(payAmount);
        payment.setMethod("VNPAY");
        payment.setPaidAt(Instant.now());
        payment.setTxnRef(txnRef);
        payment.setNote(note);
        payment.setRecordedBy(invoice.getCustomerId() != null ? invoice.getCustomerId() : 0L);
        Payment saved = payments.save(payment);

        BigDecimal paidTotal = invoice.getPaidAmount().add(payAmount);
        invoice.setPaidAmount(paidTotal);
        if (paidTotal.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (invoice.getStatus() != InvoiceStatus.OVERDUE) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoices.save(invoice);
        roomInvoices.recomputeForShare(invoice);
        events.publishPaymentReceived(saved.getId(), invoice.getId(), invoice.getCustomerId(),
                saved.getAmountPaid(), invoice.getStatus().name());
    }

    private long parseVnpayAmountVnd(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(rawAmount.trim()) / 100L;
        } catch (Exception e) {
            log.error("Invalid amount from VNPay params: {}", rawAmount);
            return 0L;
        }
    }

    private Long parsePrefixedIdFromTxnRef(String txnRef, String prefix) {
        if (txnRef == null || !txnRef.startsWith(prefix)) {
            return null;
        }
        try {
            String sub = txnRef.substring(prefix.length());
            int underscore = sub.indexOf('_');
            String idPart = underscore > 0 ? sub.substring(0, underscore) : sub;
            return Long.parseLong(idPart);
        } catch (Exception e) {
            log.warn("Could not parse {} id from txnRef: {}", prefix, txnRef);
            return null;
        }
    }

    private Long parseInvoiceIdFromTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }
        if (txnRef.startsWith("INV")) {
            return parsePrefixedIdFromTxnRef(txnRef, "INV");
        }
        try {
            int underscore = txnRef.indexOf('_');
            String idPart = underscore > 0 ? txnRef.substring(0, underscore) : txnRef;
            return Long.parseLong(idPart);
        } catch (Exception e) {
            log.warn("Could not parse invoiceId from txnRef: {}", txnRef);
            return null;
        }
    }
}
