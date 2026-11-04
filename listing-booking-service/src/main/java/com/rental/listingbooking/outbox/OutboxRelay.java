package com.rental.listingbooking.outbox;

import com.rental.listingbooking.entity.OutboxEvent;
import com.rental.listingbooking.repository.OutboxEventRepository;
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
                log.warn("Relay outbox {} bị ngắt, để tick sau gửi lại", event.getId());
            } catch (Exception failed) {
                log.warn("Gửi outbox {} thất bại, để tick sau gửi lại: {}", event.getId(), failed.toString());
            }
        }
    }
}
