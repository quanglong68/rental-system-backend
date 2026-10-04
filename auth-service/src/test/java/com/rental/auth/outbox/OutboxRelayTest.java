package com.rental.auth.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.auth.entity.OutboxEvent;
import com.rental.auth.repository.OutboxEventRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/** Unit test OutboxRelay voi KafkaTemplate mock (Task C6). */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outbox;

    @Mock
    private KafkaTemplate<String, String> kafka;

    @InjectMocks
    private OutboxRelay relay;

    @Test
    void relay_guiDungTopicKeyVaDanhDauPublished() {
        OutboxEvent event = outboxEvent("auth.events", "42", "{\"eventType\":\"AccountRegistered\"}");
        when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafka.send(eq("auth.events"), eq("42"), eq("{\"eventType\":\"AccountRegistered\"}")))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        relay.relay();

        verify(kafka).send("auth.events", "42", "{\"eventType\":\"AccountRegistered\"}");
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outbox).save(event);
    }

    @Test
    void relay_guiLoi_giuLaiDeTickSau() {
        OutboxEvent event = outboxEvent("auth.events", "43", "{}");
        when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);

        relay.relay();

        assertThat(event.getPublishedAt()).isNull();
        verify(outbox, never()).save(any(OutboxEvent.class));
    }

    @Test
    void relay_khongCoDongMoi_khongGui() {
        when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

        relay.relay();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    private static OutboxEvent outboxEvent(String topic, String key, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setTopic(topic);
        event.setEventKey(key);
        event.setEventType("AccountRegistered");
        event.setPayload(payload);
        return event;
    }
}