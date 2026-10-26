package com.rental.billingutilityservice.outbox;

import com.rental.billingutilityservice.entity.OutboxEvent;
import com.rental.billingutilityservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relay quet outbox gui Kafka (docs muc 5.3): moi 1 giay lay toi da 100 dong
 * published_at IS NULL theo created_at, gui dong bo, thanh cong thi danh dau.
 * Gui trung chap nhan duoc (consumer idempotent).
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
