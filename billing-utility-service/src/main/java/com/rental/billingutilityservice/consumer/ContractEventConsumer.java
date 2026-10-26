package com.rental.billingutilityservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.billingutilityservice.entity.ProcessedEvent;
import com.rental.billingutilityservice.repository.ProcessedEventRepository;
import com.rental.billingutilityservice.service.ContractInfoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Nghe contract.events (docs muc 5.3, 10): ContractSigned, ContractRenewed, ContractEnded.
 * Idempotent qua processed_event: event_id da co thi bo qua (cung giao dich).
 * Payload phang (ID la so); bo qua eventType khong quan tam.
 */
@Service
@RequiredArgsConstructor
public class ContractEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContractEventConsumer.class);

    private final ProcessedEventRepository processed;
    private final ContractInfoService contractInfos;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "contract.events", groupId = "billing-utility-service")
    @Transactional
    public void onMessage(String message) {
        JsonNode envelope;
        UUID eventId;
        try {
            envelope = objectMapper.readTree(message);
        } catch (Exception e) {
            log.warn("Bo qua message khong phai JSON: {}", e.toString());
            throw new IllegalStateException("Xu ly contract.events that bai", e);
        }
        String eventIdText = envelope.path("eventId").asText(null);
        try {
            eventId = UUID.fromString(eventIdText);
        } catch (Exception e) {
            log.warn("Bo qua event thieu eventId hop le");
            return;
        }
        if (processed.existsById(eventId)) {
            return;
        }
        try {
            String eventType = envelope.path("eventType").asText("");
            JsonNode payload = envelope.path("payload");
            switch (eventType) {
                case "ContractSigned" -> {
                    Long contractId = longOrNull(payload, "contractId");
                    if (contractId == null) {
                        log.warn("ContractSigned thieu contractId, bo qua");
                        markProcessed(eventId);
                        return;
                    }
                    contractInfos.upsert(contractId, longOrZero(payload, "roomId"), longOrZero(payload, "buildingId"),
                            "ACTIVE", dateOrNull(payload, "startDate"), dateOrNull(payload, "endDate"));
                }
                case "ContractRenewed" -> {
                    Long contractId = longOrNull(payload, "contractId");
                    if (contractId == null) {
                        log.warn("ContractRenewed thieu contractId, bo qua");
                        markProcessed(eventId);
                        return;
                    }
                    contractInfos.upsert(contractId, longOrZero(payload, "roomId"), longOrZero(payload, "buildingId"),
                            "ACTIVE", null, dateOrNull(payload, "newEndDate"));
                }
                case "ContractEnded" -> {
                    Long contractId = longOrNull(payload, "contractId");
                    if (contractId == null) {
                        log.warn("ContractEnded thieu contractId, bo qua");
                        markProcessed(eventId);
                        return;
                    }
                    contractInfos.upsert(contractId, longOrZero(payload, "roomId"), longOrZero(payload, "buildingId"),
                            payload.path("status").asText("ENDED"), null, dateOrNull(payload, "endDate"));
                }
                default -> {
                    // Bo qua eventType khong quan tam (docs muc 5.3).
                    markProcessed(eventId);
                    return;
                }
            }
            markProcessed(eventId);
        } catch (Exception e) {
            // Nem lai de error handler thu lai 3 lan roi day DLT (docs muc 5.3).
            log.warn("Xu ly contract.events that bai: {}", e.toString());
            throw new IllegalStateException("Xu ly contract.events that bai", e);
        }
    }

    private static Long longOrNull(JsonNode payload, String field) {
        if (!payload.has(field) || payload.path(field).isNull()) {
            return null;
        }
        long value = payload.path(field).asLong(0L);
        return value <= 0 ? null : value;
    }

    private static Long longOrZero(JsonNode payload, String field) {
        if (!payload.has(field) || payload.path(field).isNull()) {
            return 0L;
        }
        return payload.path(field).asLong(0L);
    }

    private static LocalDate dateOrNull(JsonNode payload, String field) {
        if (!payload.has(field) || payload.path(field).isNull()) {
            return null;
        }
        String text = payload.path(field).asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        } catch (Exception e) {
            log.warn("Ngay {} khong hop le '{}', bo qua", field, text);
            return null;
        }
    }

    private void markProcessed(UUID eventId) {
        try {
            ProcessedEvent event = new ProcessedEvent();
            event.setEventId(eventId);
            processed.save(event);
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            // Race idempotent: consumer khac da xu ly truoc (docs 5.3), coi nhu da xong.
            log.info("Event {} da duoc xu ly boi consumer khac", eventId);
        }
    }
}
