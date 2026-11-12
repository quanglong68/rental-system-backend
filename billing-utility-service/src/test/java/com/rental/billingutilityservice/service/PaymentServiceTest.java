package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.ContractClient;
import com.rental.billingutilityservice.client.ContractDepositInfoResponse;
import com.rental.billingutilityservice.client.MarkContractDepositPaidRequest;
import com.rental.billingutilityservice.dto.request.CreateDepositVnpayPaymentRequest;
import com.rental.billingutilityservice.dto.request.CreatePaymentRequest;
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
    private RoomInvoiceService roomInvoices;
    @Mock
    private BillingEventPublisher events;
    @Mock
    private AuthSupport auth;
    @Mock
    private ContractClient contractClient;
    @Mock
    private VnpayClient vnpayClient;
    @Mock
    private VnpayProperties vnpayProperties;
    @Mock
    private VnpaySigner vnpaySigner;

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

    @Test
    void paymentRecomputesRoomInvoice() {
        Invoice invoice = invoice(new BigDecimal("2500000"), BigDecimal.ZERO, InvoiceStatus.UNPAID);
        invoice.setRoomInvoiceId(77L);
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(payments.save(any())).thenAnswer(call -> call.getArgument(0));
        when(auth.currentUserId()).thenReturn(9L);

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setInvoiceId(1L);
        request.setAmountPaid(new BigDecimal("500000"));
        service.create(request);

        verify(roomInvoices).recomputeForShare(invoice);
    }

    @Test
    void createVnpayPaymentUrlSuccess() {
        Invoice invoice = invoice(new BigDecimal("3000000"), BigDecimal.ZERO, InvoiceStatus.UNPAID);
        invoice.setId(10L);
        when(invoices.findById(10L)).thenReturn(Optional.of(invoice));
        when(vnpayProperties.getDefaultReturnUrl()).thenReturn("http://localhost:3000/payment/vnpay-return");
        when(vnpayClient.buildPaymentUrl(any(), any(Long.class), any(), any(), any()))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=1");

        com.rental.billingutilityservice.dto.request.CreateVnpayPaymentRequest request =
                new com.rental.billingutilityservice.dto.request.CreateVnpayPaymentRequest();
        request.setInvoiceId(10L);

        com.rental.common.security.HeaderUser principal =
                new com.rental.common.security.HeaderUser("42", "CUSTOMER", java.util.List.of("CUSTOMER"));

        var response = service.createVnpayPaymentUrl(request, principal);
        org.junit.jupiter.api.Assertions.assertNotNull(response.getPaymentUrl());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("3000000"), response.getAmount());
        org.junit.jupiter.api.Assertions.assertTrue(response.getTxnRef().startsWith("INV10_"));
    }

    @Test
    void confirmVnpayPaymentSuccessMovesToPaid() {
        Invoice invoice = invoice(new BigDecimal("2000000"), BigDecimal.ZERO, InvoiceStatus.UNPAID);
        invoice.setId(15L);
        when(invoices.findByIdForUpdate(15L)).thenReturn(Optional.of(invoice));
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);
        when(payments.findByTxnRef("INV15_12345")).thenReturn(Optional.empty());
        when(payments.save(any())).thenAnswer(call -> call.getArgument(0));

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "INV15_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "200000000"); // 2,000,000 * 100
        params.put("vnp_TransactionNo", "149999");
        params.put("vnp_BankCode", "NCB");

        var response = service.confirmVnpayPayment(params);
        org.junit.jupiter.api.Assertions.assertTrue(response.isSuccess());
        org.junit.jupiter.api.Assertions.assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("2000000"), invoice.getPaidAmount());
        verify(events).publishPaymentReceived(any(), any(), any(), any(), any());
    }

    @Test
    void confirmVnpayPaymentFailsOnInvalidSignature() {
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(false);

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "INV15_12345");
        params.put("vnp_ResponseCode", "00");

        var response = service.confirmVnpayPayment(params);
        org.junit.jupiter.api.Assertions.assertFalse(response.isSuccess());
    }

    @Test
    void confirmVnpayPaymentIdempotentInsideInvoiceLock() {
        Invoice invoice = invoice(new BigDecimal("2000000"), new BigDecimal("2000000"), InvoiceStatus.PAID);
        invoice.setId(15L);
        Payment existingPayment = new Payment();
        existingPayment.setInvoice(invoice);
        existingPayment.setAmountPaid(new BigDecimal("2000000"));
        existingPayment.setTxnRef("INV15_12345");

        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);
        when(invoices.findByIdForUpdate(15L)).thenReturn(Optional.of(invoice));
        when(payments.findByTxnRef("INV15_12345")).thenReturn(Optional.of(existingPayment));

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "INV15_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "200000000");

        var response = service.confirmVnpayPayment(params);
        org.junit.jupiter.api.Assertions.assertTrue(response.isSuccess());
        assertEquals(15L, response.getInvoiceId());
        assertEquals(new BigDecimal("2000000"), response.getAmount());

        var inOrder = org.mockito.Mockito.inOrder(invoices, payments);
        inOrder.verify(invoices).findByIdForUpdate(15L);
        inOrder.verify(payments).findByTxnRef("INV15_12345");
        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void handleVnpayIpnIdempotentWhenAlreadyPaid() {
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);
        Invoice invoice = invoice(new BigDecimal("2000000"), new BigDecimal("2000000"), InvoiceStatus.PAID);
        invoice.setId(15L);
        when(invoices.findByIdForUpdate(15L)).thenReturn(Optional.of(invoice));
        when(payments.existsByTxnRef("INV15_12345")).thenReturn(true);

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "INV15_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "200000000");

        var result = service.handleVnpayIpn(params);
        org.junit.jupiter.api.Assertions.assertEquals("02", result.get("RspCode"));
    }

    @Test
    void handleVnpayIpnReturns04OnInvalidAmount() {
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "INV15_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "0");

        var result = service.handleVnpayIpn(params);
        org.junit.jupiter.api.Assertions.assertEquals("04", result.get("RspCode"));
    }

    @Test
    void handleVnpayIpnReturns01WhenOrderNotFound() {
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);
        when(invoices.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "INV99_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "100000000");

        var result = service.handleVnpayIpn(params);
        org.junit.jupiter.api.Assertions.assertEquals("01", result.get("RspCode"));
    }

    @Test
    void handleVnpayIpnReturns97OnBadSignature() {
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(false);

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "INV15_12345");

        var result = service.handleVnpayIpn(params);
        org.junit.jupiter.api.Assertions.assertEquals("97", result.get("RspCode"));
    }

    @Test
    void createDepositVnpayPaymentUrlSuccess() {
        ContractDepositInfoResponse info = new ContractDepositInfoResponse(
                9L, 42L, 7L, 12L, "A101", "DRAFT", "AWAITING_TENANT",
                new BigDecimal("6500000"), false, null
        );
        when(contractClient.getContractDepositInfo(9L)).thenReturn(info);
        when(vnpayProperties.getDefaultReturnUrl()).thenReturn("http://localhost:8443/payment/vnpay-return");
        when(vnpayClient.buildPaymentUrl(any(), any(Long.class), any(), any(), any()))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?deposit=1");

        CreateDepositVnpayPaymentRequest request = new CreateDepositVnpayPaymentRequest();
        request.setContractId(9L);

        com.rental.common.security.HeaderUser principal =
                new com.rental.common.security.HeaderUser("42", "CUSTOMER", java.util.List.of("CUSTOMER"));

        var response = service.createDepositVnpayPaymentUrl(request, principal);
        org.junit.jupiter.api.Assertions.assertTrue(response.getTxnRef().startsWith("DEP9_"));
        org.junit.jupiter.api.Assertions.assertEquals(9L, response.getContractId());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("6500000"), response.getAmount());
    }

    @Test
    void confirmDepositVnpayPaymentMarksContractDepositPaid() {
        ContractDepositInfoResponse info = new ContractDepositInfoResponse(
                9L, 42L, 7L, 12L, "A101", "DRAFT", "AWAITING_TENANT",
                new BigDecimal("6500000"), false, null
        );
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);
        when(contractClient.getContractDepositInfo(9L)).thenReturn(info);
        when(contractClient.markContractDepositPaid(any(), any(MarkContractDepositPaidRequest.class))).thenReturn(
                new ContractDepositInfoResponse(9L, 42L, 7L, 12L, "A101", "DRAFT", "AWAITING_TENANT",
                        new BigDecimal("6500000"), true, "DEP9_12345"));

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "DEP9_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "650000000");

        var response = service.confirmVnpayPayment(params);
        org.junit.jupiter.api.Assertions.assertTrue(response.isSuccess());
        org.junit.jupiter.api.Assertions.assertEquals(9L, response.getContractId());
        verify(contractClient).markContractDepositPaid(org.mockito.ArgumentMatchers.eq(9L), any(MarkContractDepositPaidRequest.class));
    }

    @Test
    void confirmDepositVnpayPaymentIdempotentForSameTxnRef() {
        ContractDepositInfoResponse info = new ContractDepositInfoResponse(
                9L, 42L, 7L, 12L, "A101", "DRAFT", "AWAITING_TENANT",
                new BigDecimal("6500000"), true, "DEP9_12345"
        );
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);
        when(contractClient.getContractDepositInfo(9L)).thenReturn(info);

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "DEP9_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "650000000");

        var response = service.confirmVnpayPayment(params);
        org.junit.jupiter.api.Assertions.assertTrue(response.isSuccess());
        org.junit.jupiter.api.Assertions.assertEquals(9L, response.getContractId());
        verify(contractClient, org.mockito.Mockito.never()).markContractDepositPaid(any(), any());
    }

    @Test
    void handleDepositVnpayIpnSuccess() {
        ContractDepositInfoResponse info = new ContractDepositInfoResponse(
                9L, 42L, 7L, 12L, "A101", "DRAFT", "AWAITING_TENANT",
                new BigDecimal("6500000"), false, null
        );
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);
        when(contractClient.getContractDepositInfo(9L)).thenReturn(info);

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "DEP9_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "650000000");

        var result = service.handleVnpayIpn(params);
        org.junit.jupiter.api.Assertions.assertEquals("00", result.get("RspCode"));
        verify(contractClient).markContractDepositPaid(org.mockito.ArgumentMatchers.eq(9L), any(MarkContractDepositPaidRequest.class));
    }

    @Test
    void createDepositVnpayPaymentUrlUnwrapsNoFallbackNotFound() {
        when(contractClient.getContractDepositInfo(999L)).thenThrow(
                new org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException(
                        "No fallback", new com.rental.common.error.NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy")));

        CreateDepositVnpayPaymentRequest request = new CreateDepositVnpayPaymentRequest();
        request.setContractId(999L);
        com.rental.common.security.HeaderUser principal =
                new com.rental.common.security.HeaderUser("42", "CUSTOMER", java.util.List.of("CUSTOMER"));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.rental.common.error.NotFoundException.class,
                () -> service.createDepositVnpayPaymentUrl(request, principal));
    }

    @Test
    void confirmDepositVnpayPaymentUnwrapsNoFallbackNotFound() {
        when(vnpayProperties.getHashSecret()).thenReturn("SECRET123");
        when(vnpaySigner.verify(any(), any())).thenReturn(true);
        when(contractClient.getContractDepositInfo(999L)).thenThrow(
                new org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException(
                        "No fallback", new com.rental.common.error.NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy")));

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("vnp_TxnRef", "DEP999_12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "650000000");

        var response = service.confirmVnpayPayment(params);
        org.junit.jupiter.api.Assertions.assertFalse(response.isSuccess());
        org.junit.jupiter.api.Assertions.assertTrue(response.getMessage().contains("#999"));
    }
}
