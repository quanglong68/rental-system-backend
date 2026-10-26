package fptu.semester8.mss301.propertiesservice.features.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final TransactionTemplate transactions = new TransactionTemplate(manager);
    private final UUID eventId = UUID.randomUUID();
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet row = mock(ResultSet.class);
                    when(row.getObject(1, UUID.class)).thenReturn(eventId);
                    when(row.getString(2)).thenReturn("property.events");
                    when(row.getString(3)).thenReturn("12");
                    when(row.getString(4)).thenReturn("RoomPriceChanged");
                    when(row.getString(5)).thenReturn("{\"correlationId\":\"c-1\"}");
                    return List.of(mapper.mapRow(row, 0));
                });
        relay = new OutboxRelay(jdbc, transactions, kafka, new JsonMapper(), 1, 30, 5, 10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void marksPublishedOnlyAfterBrokerAcknowledges() {
        when(kafka.send("property.events", "12", "{\"correlationId\":\"c-1\"}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        verify(jdbc).update(contains("published_at = CURRENT_TIMESTAMP"), eq(eventId), any(UUID.class));
        verify(jdbc, never()).update(contains("attempt_count = attempt_count + 1"), any(), any(), any());
    }

    @Test
    void leavesFailedSendRetryable() {
        when(kafka.send("property.events", "12", "{\"correlationId\":\"c-1\"}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        relay.relay();

        verify(jdbc).update(contains("attempt_count = attempt_count + 1"), eq(5), eq(eventId), any(UUID.class));
        verify(jdbc, never()).update(contains("published_at = CURRENT_TIMESTAMP"), any(), any());
    }
}
