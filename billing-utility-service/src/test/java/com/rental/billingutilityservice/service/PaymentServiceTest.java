package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.dto.request.CreatePaymentRequest;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.Payment;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.outbox.BillingEventPublisher;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.PaymentRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Thanh toan (docs muc 9.5): chuyen trang thai theo tong tra; chan tra thua va tra lap. */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private InvoiceRepository invoices;
    @Mock
    private PaymentRepository payments;
    @Mock
    private BillingEventPublisher events;
    @Mock
    private AuthSupport auth;

    @InjectMocks
    private PaymentService service;

    private Invoice invoice(BigDecimal total, BigDecimal paid, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setBuildingId(7L);
        invoice.setCustomerId(42L);
        invoice.setMonth(9);
        invoice.setYear(2026);
        invoice.setTotalAmount(total);
        invoice.setPaidAmount(paid);
        invoice.setStatus(status);
        invoice.setDueDate(LocalDate.of(2026, 10, 8));
        return invoice;
    }

    @Test
    void partialPaymentMovesToPartiallyPaid() {
        Invoice invoice = invoice(new BigDecimal("2500000"), BigDecimal.ZERO, InvoiceStatus.UNPAID);
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(payments.save(any())).thenAnswer(call -> call.getArgument(0));
        when(auth.currentUserId()).thenReturn(9L);

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setInvoiceId(1L);
        request.setAmountPaid(new BigDecimal("1000000"));
        var response = service.create(request);

        assertEquals(new BigDecimal("1000000"), response.getAmountPaid());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        assertEquals(new BigDecimal("1000000"), invoice.getPaidAmount());
        verify(events).publishPaymentReceived(any(), any(), any(), any(), any());
    }

    @Test
    void fullPaymentMovesToPaid() {
        Invoice invoice = invoice(new BigDecimal("2500000"), new BigDecimal("1000000"),
                InvoiceStatus.PARTIALLY_PAID);
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(payments.save(any())).thenAnswer(call -> {
            Payment payment = call.getArgument(0);
            return payment;
        });
        when(auth.currentUserId()).thenReturn(9L);

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setInvoiceId(1L);
        request.setAmountPaid(new BigDecimal("1500000"));
        service.create(request);

        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void overpaymentIsRejected() {
        Invoice invoice = invoice(new BigDecimal("2500000"), new BigDecimal("2000000"),
                InvoiceStatus.PARTIALLY_PAID);
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setInvoiceId(1L);
        request.setAmountPaid(new BigDecimal("600000"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals("AMOUNT_EXCEEDS_REMAINING", ex.getCode());
    }

    @Test
    void paidInvoiceRejectsMorePayment() {
        Invoice invoice = invoice(new BigDecimal("2500000"), new BigDecimal("2500000"), InvoiceStatus.PAID);
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setInvoiceId(1L);
        request.setAmountPaid(new BigDecimal("1000"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals("INVOICE_ALREADY_PAID", ex.getCode());
    }
}
