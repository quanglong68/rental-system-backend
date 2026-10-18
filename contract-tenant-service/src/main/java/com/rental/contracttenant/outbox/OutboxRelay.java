package com.rental.contracttenant.outbox;

import com.rental.contracttenant.entity.OutboxEvent;
import com.rental.contracttenant.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka (docs muc 5.3): every second, up to 100 unpublished rows oldest
 * first, sent synchronously; a sent row gets {@code published_at}. A failed send is left alone so
 * the next tick retries — duplicates are accepted because consumers are idempotent.
 * Mirrors the auth-service relay; not shared because it is bound to that service's own database.
 */
@Service
@RequiredArgsConstructor
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                kafka.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(10, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                outbox.save(event);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.warn("Relay outbox {} bi ngat, de tick sau gui lai", event.getId());
            } catch (Exception failed) {
                log.warn("Gui outbox {} that bai, de tick sau gui lai: {}", event.getId(), failed.toString());
            }
        }
    }
}
