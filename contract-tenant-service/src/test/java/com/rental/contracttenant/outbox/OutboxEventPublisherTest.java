package com.rental.contracttenant.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.OutboxEvent;
import com.rental.contracttenant.repository.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The outbox row is the only thing the business transaction writes for Kafka, so its shape IS the
 * cross-service event contract (docs muc 5.3 + muc 10). Mirrors the auth-service publisher test.
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outbox;

    @InjectMocks
    private OutboxEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishContractSigned_writesOneRowCarryingTheStandardEnvelope() throws Exception {
        Contract contract = new Contract();
        contract.setId(9L);
        contract.setRoomId(12L);
        contract.setBuildingId(3L);
        contract.setStartDate(LocalDate.of(2026, 12, 1));
        contract.setEndDate(LocalDate.of(2027, 11, 30));
        contract.setRentAmount(new BigDecimal("4000000"));

        publisher.publishContractSigned(contract, List.of(77L));

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(saved.capture());
        OutboxEvent event = saved.getValue();

        assertThat(event.getTopic()).isEqualTo("contract.events");
        assertThat(event.getEventType()).isEqualTo("ContractSigned");
        // Message key is contractId so every event of one contract lands on one partition (docs 5.3).
        assertThat(event.getEventKey()).isEqualTo("9");

        JsonNode envelope = objectMapper.readTree(event.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("ContractSigned");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(event.getId().toString());
        assertThat(envelope.hasNonNull("occurredAt")).isTrue();
        assertThat(envelope.hasNonNull("correlationId")).isTrue();

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("contractId").asLong()).isEqualTo(9L);
        assertThat(payload.get("roomId").asLong()).isEqualTo(12L);
        assertThat(payload.get("buildingId").asLong()).isEqualTo(3L);
        assertThat(payload.get("startDate").asText()).isEqualTo("2026-12-01");
        assertThat(payload.get("endDate").asText()).isEqualTo("2027-11-30");
        assertThat(payload.get("rentAmount").asLong()).isEqualTo(4000000L);
        assertThat(payload.get("tenantIds").get(0).asLong()).isEqualTo(77L);
    }
}
