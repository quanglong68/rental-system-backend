package com.rental.auth.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.auth.entity.AccountType;
import com.rental.auth.entity.OutboxEvent;
import com.rental.auth.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test OutboxEventPublisher: ghi dung 1 dong outbox, envelope dung chuan (Task C6). */
@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outbox;

    @InjectMocks
    private OutboxEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publish_ghiDungEnvelopeAccountRegistered() throws Exception {
        publisher.publishAccountRegistered(42L, AccountType.CUSTOMER, "khach@mail.com", "Khach Hang",
                "khach@mail.com", null);

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(saved.capture());
        OutboxEvent event = saved.getValue();
        assertThat(event.getTopic()).isEqualTo("auth.events");
        assertThat(event.getEventKey()).isEqualTo("42");
        assertThat(event.getEventType()).isEqualTo("AccountRegistered");

        JsonNode envelope = objectMapper.readTree(event.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("AccountRegistered");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(event.getId().toString());
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();
        assertThat(envelope.get("correlationId").asText()).isNotBlank();
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("accountId").asLong()).isEqualTo(42L);
        assertThat(payload.get("accountType").asText()).isEqualTo("CUSTOMER");
        assertThat(payload.get("username").asText()).isEqualTo("khach@mail.com");
        assertThat(payload.get("fullName").asText()).isEqualTo("Khach Hang");
        assertThat(payload.get("email").asText()).isEqualTo("khach@mail.com");
        assertThat(payload.get("phone").isNull()).isTrue();
    }

    @Test
    void publish_moiLanSinhEventIdKhacNhau() {
        publisher.publishAccountRegistered(1L, AccountType.STAFF, "a@b.com", "A", null, "0912345678");
        publisher.publishAccountRegistered(1L, AccountType.STAFF, "a@b.com", "A", null, "0912345678");

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getId()).isNotEqualTo(saved.getAllValues().get(1).getId());
    }
}