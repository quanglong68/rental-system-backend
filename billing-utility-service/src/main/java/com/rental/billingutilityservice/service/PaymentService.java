package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.dto.request.CreatePaymentRequest;
import com.rental.billingutilityservice.dto.response.PaymentResponse;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.Payment;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.outbox.BillingEventPublisher;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.PaymentRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Thanh toan (docs muc 9.5): staff ghi nhan tien mat tung nguoi. Hoa don chuyen
 * UNPAID → PARTIALLY_PAID → PAID theo tong da tra; khong cho tra qua so con no
 * (422 AMOUNT_EXCEEDS_REMAINING) hoac khi da PAID (422 INVOICE_ALREADY_PAID).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final BillingEventPublisher events;
    private final AuthSupport auth;

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        Invoice invoice = invoices.findById(request.getInvoiceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay hoa don"));
        auth.requireBuildingScope(invoice.getBuildingId());
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessException("INVOICE_ALREADY_PAID", "Hoa don da thanh toan du",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        BigDecimal remaining = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        if (request.getAmountPaid().compareTo(remaining) > 0) {
            throw new BusinessException("AMOUNT_EXCEEDS_REMAINING", "So tien vuot qua so con no",
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
        events.publishPaymentReceived(saved.getId(), invoice.getId(), invoice.getCustomerId(),
                saved.getAmountPaid(), invoice.getStatus().name());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByInvoice(Long invoiceId, com.rental.common.security.HeaderUser principal) {
        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay hoa don"));
        boolean admin = "ADMIN".equalsIgnoreCase(principal.getUserType())
                || principal.getRoles().contains("ADMIN");
        boolean owner = String.valueOf(invoice.getCustomerId()).equals(principal.getUserId());
        if (!admin && !owner) {
            if ("CUSTOMER".equalsIgnoreCase(principal.getUserType())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Khong phai hoa don cua ban");
            }
            auth.requireBuildingScope(invoice.getBuildingId());
        }
        return payments.findByInvoice_IdOrderByPaidAtAsc(invoiceId).stream().map(this::toResponse).toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder().id(payment.getId()).invoiceId(payment.getInvoice().getId())
                .amountPaid(payment.getAmountPaid()).method(payment.getMethod()).paidAt(payment.getPaidAt())
                .note(payment.getNote()).recordedBy(payment.getRecordedBy()).build();
    }
}
