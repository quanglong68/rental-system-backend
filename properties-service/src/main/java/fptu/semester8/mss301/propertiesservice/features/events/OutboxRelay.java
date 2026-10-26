package fptu.semester8.mss301.propertiesservice.features.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final int batchSize;
    private final int leaseSeconds;
    private final int retryDelaySeconds;
    private final int sendTimeoutSeconds;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions,
                       KafkaTemplate<String, String> kafka, ObjectMapper json,
                       @Value("${property.outbox.batch-size}") int batchSize,
                       @Value("${property.outbox.lease-seconds}") int leaseSeconds,
                       @Value("${property.outbox.retry-delay-seconds}") int retryDelaySeconds,
                       @Value("${property.kafka.send-timeout-seconds}") int sendTimeoutSeconds) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.kafka = kafka;
        this.json = json;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.retryDelaySeconds = retryDelaySeconds;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${property.outbox.poll-delay-ms}")
    public void relay() {
        for (int i = 0; i < batchSize; i++) {
            UUID owner = UUID.randomUUID();
            var pending = transactions.execute(status -> jdbc.query("""
                UPDATE outbox_event o
                   SET claimed_by = ?, claimed_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                 WHERE o.outbox_event_id IN (
                     SELECT outbox_event_id FROM outbox_event
                      WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                        AND (claimed_until IS NULL OR claimed_until < CURRENT_TIMESTAMP)
                      ORDER BY created_at, outbox_event_id
                      LIMIT 1 FOR UPDATE SKIP LOCKED
                 )
                RETURNING o.outbox_event_id, o.topic, o.event_key, o.event_type, o.payload::text
                """, (rs, row) -> new Pending(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5)), owner, leaseSeconds));
            if (pending == null || pending.isEmpty()) return;
            Pending event = pending.getFirst();
            String correlationId = json.readTree(event.payload()).path("correlationId").asString("");
            try {
                kafka.send(event.topic(), event.key(), event.payload())
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
                transactions.executeWithoutResult(status -> jdbc.update("""
                        UPDATE outbox_event SET published_at = CURRENT_TIMESTAMP,
                            claimed_by = NULL, claimed_until = NULL, updated_at = CURRENT_TIMESTAMP
                        WHERE outbox_event_id = ? AND claimed_by = ? AND published_at IS NULL
                        """, event.id(), owner));
                log.info("Published eventId={} eventType={} aggregateId={} correlationId={}",
                        event.id(), event.type(), event.key(), correlationId);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failed(event, owner, correlationId, interrupted);
                return;
            } catch (Exception failure) {
                failed(event, owner, correlationId, failure);
            }
        }
    }

    private void failed(Pending event, UUID owner, String correlationId, Exception failure) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE outbox_event SET claimed_by = NULL, claimed_until = NULL,
                    attempt_count = attempt_count + 1,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    updated_at = CURRENT_TIMESTAMP
                WHERE outbox_event_id = ? AND claimed_by = ? AND published_at IS NULL
                """, retryDelaySeconds, event.id(), owner));
        log.warn("Publish failed eventId={} eventType={} aggregateId={} correlationId={}; will retry",
                event.id(), event.type(), event.key(), correlationId, failure);
    }

    private record Pending(UUID id, String topic, String key, String type, String payload) {}
}
