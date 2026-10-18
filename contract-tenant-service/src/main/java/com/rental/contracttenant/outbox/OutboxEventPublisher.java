package com.rental.contracttenant.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rental.common.constant.Headers;
import com.rental.common.event.EventEnvelope;
import com.rental.common.security.HeaderAuthenticationFilter;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.OutboxEvent;
import com.rental.contracttenant.repository.OutboxEventRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes a {@code ContractSigned} row into the outbox <em>inside the caller's transaction</em>
 * (docs muc 5.3), so the contract status change, the first contract_tenant row and the event row
 * commit or roll back together. Kafka is never called here: a crash between commit and send would
 * lose the event, which the relay's retry exists to prevent.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    /** Topic contract-tenant-service owns; message key is contractId (docs muc 5.3). */
    public static final String CONTRACT_EVENTS_TOPIC = "contract.events";

    /** Event name for a signed contract (docs muc 10). */
    public static final String EVENT_CONTRACT_SIGNED = "ContractSigned";

    private final OutboxEventRepository outbox;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Enqueues one ContractSigned event. Must be called from inside the signing transaction.
     *
     * @param contract the signed contract, already ACTIVE with a start and end date
     * @param tenantIds customer IDs currently occupying the contract (docs muc 10)
     */
    public void publishContractSigned(Contract contract, List<Long> tenantIds) {
        ContractSignedPayload payload = ContractSignedPayload.of(contract, tenantIds);
        EventEnvelope<ContractSignedPayload> envelope =
                EventEnvelope.of(EVENT_CONTRACT_SIGNED, payload, correlationId());

        OutboxEvent event = new OutboxEvent();
        // eventId becomes the outbox primary key too: a retried relay send reuses the same event ID,
        // so an idempotent consumer (processed_event) drops the duplicate.
        event.setId(envelope.getEventId());
        event.setTopic(CONTRACT_EVENTS_TOPIC);
        event.setEventKey(String.valueOf(contract.getId()));
        event.setEventType(EVENT_CONTRACT_SIGNED);
        try {
            event.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Khong serialize duoc ContractSigned", impossible);
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
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
                fromHeader = servlet.getRequest().getHeader(Headers.X_CORRELATION_ID);
            }
        } catch (Exception ignored) {
            // Tracing must not break the business call.
        }
        return fromHeader != null && !fromHeader.isBlank() ? fromHeader : UUID.randomUUID().toString();
    }
}
