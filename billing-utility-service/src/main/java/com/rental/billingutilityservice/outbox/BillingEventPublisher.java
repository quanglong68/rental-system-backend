package com.rental.billingutilityservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rental.billingutilityservice.entity.OutboxEvent;
import com.rental.billingutilityservice.repository.OutboxEventRepository;
import com.rental.common.constant.Headers;
import com.rental.common.event.EventEnvelope;
import com.rental.common.security.HeaderAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ghi event vao outbox CUNG giao dich nghiep vu (docs muc 5.3). CAM goi kafkaTemplate.send
 * truc tiep trong service: neu crash giua chung se mat event; ghi outbox thi relay gui lai.
 * Billing phat: InvoiceIssued, PaymentReceived, InvoiceOverdue, MeterReadingMissing (muc 10).
 */
@Service
@RequiredArgsConstructor
public class BillingEventPublisher {

    public static final String BILLING_EVENTS_TOPIC = "billing.events";

    public static final String EVENT_INVOICE_ISSUED = "InvoiceIssued";
    public static final String EVENT_PAYMENT_RECEIVED = "PaymentReceived";
    public static final String EVENT_INVOICE_OVERDUE = "InvoiceOverdue";
    public static final String EVENT_METER_READING_MISSING = "MeterReadingMissing";

    private final OutboxEventRepository outbox;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void publishInvoiceIssued(Long invoiceId, Long customerId, Long contractId, Long roomId, Long buildingId,
            int month, int year, BigDecimal totalAmount, LocalDate dueDate) {
        record Payload(Long invoiceId, Long customerId, Long contractId, Long roomId, Long buildingId, Integer month,
                Integer year, BigDecimal totalAmount, LocalDate dueDate) {
        }
        save(EVENT_INVOICE_ISSUED, String.valueOf(invoiceId), new Payload(invoiceId, customerId, contractId, roomId,
                buildingId, month, year, totalAmount, dueDate));
    }

    public void publishPaymentReceived(Long paymentId, Long invoiceId, Long customerId, BigDecimal amountPaid,
            String invoiceStatus) {
        record Payload(Long paymentId, Long invoiceId, Long customerId, BigDecimal amountPaid, String invoiceStatus) {
        }
        save(EVENT_PAYMENT_RECEIVED, String.valueOf(invoiceId),
                new Payload(paymentId, invoiceId, customerId, amountPaid, invoiceStatus));
    }

    public void publishInvoiceOverdue(Long invoiceId, Long customerId, Long buildingId, int month, int year,
            BigDecimal totalAmount, LocalDate dueDate) {
        record Payload(Long invoiceId, Long customerId, Long buildingId, Integer month, Integer year,
                BigDecimal totalAmount, LocalDate dueDate) {
        }
        save(EVENT_INVOICE_OVERDUE, String.valueOf(invoiceId),
                new Payload(invoiceId, customerId, buildingId, month, year, totalAmount, dueDate));
    }

    public void publishMeterReadingMissing(Long roomId, Long buildingId, int month, int year, List<Long> meterIds) {
        record Payload(Long roomId, Long buildingId, Integer month, Integer year, List<Long> meterIds) {
        }
        save(EVENT_METER_READING_MISSING, String.valueOf(roomId),
                new Payload(roomId, buildingId, month, year, meterIds));
    }

    private void save(String eventType, String eventKey, Object payload) {
        EventEnvelope<Object> envelope = EventEnvelope.of(eventType, payload, correlationId());
        OutboxEvent event = new OutboxEvent();
        event.setId(envelope.getEventId());
        event.setTopic(BILLING_EVENTS_TOPIC);
        event.setEventKey(eventKey);
        event.setEventType(eventType);
        try {
            event.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Khong serialize duoc " + eventType, impossible);
        }
        outbox.save(event);
    }

    private static String correlationId() {
        String fromMdc = MDC.get(HeaderAuthenticationFilter.MDC_CORRELATION_ID);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromHeader = null;
        try {
            var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
                fromHeader = servlet.getRequest().getHeader(Headers.X_CORRELATION_ID);
            }
        } catch (Exception ignored) {
            // Khong de truy vet lam vo nghiep vu chinh.
        }
        return fromHeader != null && !fromHeader.isBlank() ? fromHeader : UUID.randomUUID().toString();
    }
}
