package com.rental.asset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.asset.entity.OutboxEvent;
import com.rental.asset.repository.OutboxEventRepository;
import com.rental.common.event.EventEnvelope;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void recordEvent(String eventType, Object eventPayload, String aggregateId) {
        try {
            EventEnvelope eventEnvelope = EventEnvelope.of(eventType, eventPayload, MDC.get("correlationId"));

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setId(eventEnvelope.getEventId());
            outboxEvent.setAggregateType("asset");
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(objectMapper.valueToTree(eventEnvelope));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ghi outbox", exception);
        }
    }
}
