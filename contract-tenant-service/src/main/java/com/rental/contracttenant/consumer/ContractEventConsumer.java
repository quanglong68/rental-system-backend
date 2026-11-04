package com.rental.contracttenant.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.contracttenant.dto.CreateDraftContractRequest;
import com.rental.contracttenant.dto.RoommateApplicationAcceptedEvent;
import com.rental.contracttenant.entity.ProcessedEvent;
import com.rental.contracttenant.repository.ProcessedEventRepository;
import com.rental.contracttenant.service.ContractService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumes {@code listing.events} (spec 9.4 "Event nghe": RentalRequestAccepted,
 * RoommateApplicationAccepted). Other event types are ignored, as spec 5.3 prescribes.
 *
 * <p>Business work and the {@code processed_event} row run in one transaction, so a re-delivered
 * event is a no-op. A malformed envelope or unusable payload is deliberately NOT swallowed: it is
 * thrown so the container's {@link com.rental.contracttenant.config.KafkaConfig} error handler retries
 * and finally routes it to {@code listing.events.DLT} instead of silently losing the message.
 */
@Component
@RequiredArgsConstructor
public class ContractEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContractEventConsumer.class);

    private final ContractService contractService;
    private final ProcessedEventRepository processedEvents;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "listing.events", groupId = "contract-tenant-service")
    public void consume(String message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Sự kiện không phải JSON hợp lệ", malformed);
        }

        String eventType = text(root, "eventType");
        String eventIdStr = text(root, "eventId");
        if (eventType == null || eventIdStr == null) {
            throw new IllegalArgumentException("Sự kiện thiếu eventType hoặc eventId");
        }

        UUID eventId = UUID.fromString(eventIdStr);
        JsonNode payload = root.path("payload");

        switch (eventType) {
            case "RentalRequestAccepted" -> handleRentalRequestAccepted(eventId, payload);
            case "RoommateApplicationAccepted" -> handleRoommateApplicationAccepted(eventId, payload);
            default -> log.debug("Bỏ qua eventType không quan tâm: {}", eventType);
        }
    }

    private void handleRentalRequestAccepted(UUID eventId, JsonNode payload) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            if (processedEvents.existsById(eventId)) {
                log.info("Event {} đã được xử lý, bỏ qua (idempotent)", eventId);
                return;
            }

            CreateDraftContractRequest request = new CreateDraftContractRequest();
            request.setRentalRequestId(requiredLong(payload, "rentalRequestId"));
            request.setRoomId(requiredLong(payload, "roomId"));
            request.setBuildingId(requiredLong(payload, "buildingId"));
            request.setCustomerId(requiredLong(payload, "customerId"));
            request.setPrice(new BigDecimal(requiredText(payload, "price")));
            request.setMoveInDate(LocalDate.parse(requiredText(payload, "moveInDate")));
            String deposit = text(payload, "depositAmount");
            if (deposit != null && !deposit.isBlank()) {
                request.setDepositAmount(new BigDecimal(deposit));
            }
            String minTermMonths = text(payload, "minTermMonths");
            if (minTermMonths != null && !minTermMonths.isBlank()) {
                request.setMinTermMonths(Integer.valueOf(minTermMonths));
            }
            applySnapshots(payload, request);

            contractService.createDraft(request);

            processedEvents.save(new ProcessedEvent(eventId));
        });
    }

    private void handleRoommateApplicationAccepted(UUID eventId, JsonNode payload) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            if (processedEvents.existsById(eventId)) {
                log.info("Event {} đã được xử lý, bỏ qua (idempotent)", eventId);
                return;
            }

            RoommateApplicationAcceptedEvent event = new RoommateApplicationAcceptedEvent(
                    requiredLong(payload, "applicationId"),
                    requiredLong(payload, "postId"),
                    requiredLong(payload, "roomId"),
                    requiredLong(payload, "buildingId"),
                    requiredLong(payload, "ownerCustomerId"),
                    requiredLong(payload, "applicantCustomerId"),
                    text(payload, "fullName"),
                    text(payload, "phone"),
                    text(payload, "cccdNumber"),
                    date(payload, "moveInDate")
            );

            contractService.processRoommateAccepted(event);

            processedEvents.save(new ProcessedEvent(eventId));
        });
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /** Contract terms and identity snapshots are optional so events published before Phase C stay valid. */
    private static void applySnapshots(JsonNode payload, CreateDraftContractRequest request) {
        request.setRoomNumber(text(payload, "roomNumber"));
        request.setBuildingName(text(payload, "buildingName"));
        request.setRoomAddress(text(payload, "roomAddress"));
        request.setElectricityPrice(decimal(payload, "electricityPrice"));
        request.setWaterPrice(decimal(payload, "waterPrice"));
        request.setWaterUnit(text(payload, "waterUnit"));
        request.setPaymentMethod(text(payload, "paymentMethod"));
        request.setPaymentDueDay(integer(payload, "paymentDueDay"));
        request.setAvailableFrom(date(payload, "availableFrom"));

        request.setLessorName(text(payload, "lessorName"));
        request.setLessorDateOfBirth(date(payload, "lessorDateOfBirth"));
        request.setLessorIdNumber(text(payload, "lessorIdNumber"));
        request.setLessorIdIssueDate(date(payload, "lessorIdIssueDate"));
        request.setLessorIdIssuePlace(text(payload, "lessorIdIssuePlace"));
        request.setLessorPhone(text(payload, "lessorPhone"));
        request.setLessorAddress(text(payload, "lessorAddress"));

        request.setTenantFullName(text(payload, "tenantFullName"));
        request.setTenantDateOfBirth(date(payload, "tenantDateOfBirth"));
        request.setTenantGender(text(payload, "tenantGender"));
        request.setTenantPhone(text(payload, "tenantPhone"));
        request.setTenantEmail(text(payload, "tenantEmail"));
        request.setTenantPermanentAddress(text(payload, "tenantPermanentAddress"));
        request.setTenantOccupation(text(payload, "tenantOccupation"));
        request.setTenantCccdNumber(text(payload, "tenantCccdNumber"));
        request.setTenantCccdIssueDate(date(payload, "tenantCccdIssueDate"));
        request.setTenantCccdIssuePlace(text(payload, "tenantCccdIssuePlace"));
        request.setTenantCccdFrontKey(text(payload, "tenantCccdFrontKey"));
        request.setTenantCccdBackKey(text(payload, "tenantCccdBackKey"));
    }

    private static BigDecimal decimal(JsonNode payload, String field) {
        String value = text(payload, field);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static Integer integer(JsonNode payload, String field) {
        String value = text(payload, field);
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private static LocalDate date(JsonNode payload, String field) {
        String value = text(payload, field);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static String requiredText(JsonNode payload, String field) {
        String value = text(payload, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payload thiếu " + field);
        }
        return value;
    }

    private static Long requiredLong(JsonNode payload, String field) {
        return Long.valueOf(requiredText(payload, field));
    }
}
