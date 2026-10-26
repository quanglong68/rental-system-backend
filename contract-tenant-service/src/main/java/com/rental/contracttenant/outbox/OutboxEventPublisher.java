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

@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    public static final String CONTRACT_EVENTS_TOPIC = "contract.events";

    public static final String EVENT_CONTRACT_SIGNED = "ContractSigned";
    public static final String EVENT_CONTRACT_RENEWED = "ContractRenewed";
    public static final String EVENT_CONTRACT_ENDED = "ContractEnded";
    public static final String EVENT_TENANT_ADDED = "TenantAdded";
    public static final String EVENT_TENANT_ADD_REJECTED = "TenantAddRejected";
    public static final String EVENT_TENANT_REMOVED = "TenantRemoved";
    public static final String EVENT_MOVE_OUT_REQUESTED = "MoveOutRequested";
    public static final String EVENT_MOVE_OUT_APPROVED = "MoveOutApproved";

    private final OutboxEventRepository outbox;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void publishContractSigned(Contract contract, List<Long> tenantIds) {
        ContractSignedPayload payload = ContractSignedPayload.of(contract, tenantIds);
        saveOutboxEvent(EVENT_CONTRACT_SIGNED, String.valueOf(contract.getId()), payload);
    }

    public void publishContractRenewed(ContractRenewedPayload payload) {
        saveOutboxEvent(EVENT_CONTRACT_RENEWED, String.valueOf(payload.getContractId()), payload);
    }

    public void publishContractEnded(ContractEndedPayload payload) {
        saveOutboxEvent(EVENT_CONTRACT_ENDED, String.valueOf(payload.getContractId()), payload);
    }

    public void publishTenantAdded(TenantAddedPayload payload) {
        saveOutboxEvent(EVENT_TENANT_ADDED, String.valueOf(payload.getContractId()), payload);
    }

    public void publishTenantAddRejected(TenantAddRejectedPayload payload) {
        saveOutboxEvent(EVENT_TENANT_ADD_REJECTED, String.valueOf(payload.getRoomId()), payload);
    }

    public void publishTenantRemoved(TenantRemovedPayload payload) {
        saveOutboxEvent(EVENT_TENANT_REMOVED, String.valueOf(payload.getContractId()), payload);
    }

    public void publishMoveOutRequested(MoveOutRequestedPayload payload) {
        saveOutboxEvent(EVENT_MOVE_OUT_REQUESTED, String.valueOf(payload.getContractId()), payload);
    }

    public void publishMoveOutApproved(MoveOutApprovedPayload payload) {
        saveOutboxEvent(EVENT_MOVE_OUT_APPROVED, String.valueOf(payload.getContractId()), payload);
    }

    private <T> void saveOutboxEvent(String eventType, String eventKey, T payload) {
        EventEnvelope<T> envelope = EventEnvelope.of(eventType, payload, correlationId());
        OutboxEvent event = new OutboxEvent();
        event.setId(envelope.getEventId());
        event.setTopic(CONTRACT_EVENTS_TOPIC);
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
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
                fromHeader = servlet.getRequest().getHeader(Headers.X_CORRELATION_ID);
            }
        } catch (Exception ignored) {
        }
        return fromHeader != null && !fromHeader.isBlank() ? fromHeader : UUID.randomUUID().toString();
    }
}
