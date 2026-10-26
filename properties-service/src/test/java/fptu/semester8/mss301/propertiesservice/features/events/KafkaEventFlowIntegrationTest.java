package fptu.semester8.mss301.propertiesservice.features.events;

import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.OccupantProjectionService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KafkaEventFlowIntegrationTest {
    @Test
    void outboxRetryDltAndProcessedMarkerRollback() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:17-alpine");
             var kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))) {
            postgres.start();
            kafka.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(),
                    postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE room_price (room_price_id BIGINT PRIMARY KEY, effective_from DATE NOT NULL, effective_to DATE)");
            try (var connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection,
                        new ClassPathResource("db/migration/V5__create_event_tables.sql"));
                ScriptUtils.executeSqlScript(connection,
                        new ClassPathResource("db/migration/V16__add_event_delivery_state.sql"));
            }
            var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            try (var admin = AdminClient.create(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    kafka.getBootstrapServers()))) {
                admin.createTopics(List.of(new NewTopic("property.events", 3, (short) 1),
                        new NewTopic("contract.events.DLT", 3, (short) 1))).all().get();
            }
            var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.ACKS_CONFIG, "all",
                    ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true));
            var template = new KafkaTemplate<>(producerFactory);
            try {
                UUID eventId = UUID.randomUUID();
                String envelope = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"BuildingUpdated\",\"version\":1,\"payload\":{\"buildingId\":44}}";
                jdbc.update("""
                        INSERT INTO outbox_event(outbox_event_id, topic, event_key, event_type, payload)
                        VALUES (?, 'property.events', '44', 'BuildingUpdated', CAST(? AS jsonb))
                        """, eventId, envelope);
                @SuppressWarnings("unchecked")
                KafkaTemplate<String, String> failedKafka = mock(KafkaTemplate.class);
                when(failedKafka.send(anyString(), anyString(), anyString()))
                        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
                new OutboxRelay(jdbc, tx, failedKafka, new JsonMapper(), 100, 30, 0, 10).relay();
                assertNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE outbox_event_id = ?",
                        Object.class, eventId));
                assertEquals(1, jdbc.queryForObject("SELECT attempt_count FROM outbox_event WHERE outbox_event_id = ?",
                        Integer.class, eventId));
                new OutboxRelay(jdbc, tx, template, new JsonMapper(), 100, 30, 0, 10).relay();
                assertNotNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE outbox_event_id = ?",
                        Object.class, eventId));

                try (var consumer = new KafkaConsumer<String, String>(Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"))) {
                    consumer.assign(List.of(new TopicPartition("property.events", 0),
                            new TopicPartition("property.events", 1), new TopicPartition("property.events", 2)));
                    assertTrue(received(consumer, "property.events",
                            record -> record.key().equals("44") && record.value().contains(eventId.toString())));
                }

                var recoverer = new DeadLetterPublishingRecoverer(template,
                        (record, failure) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
                recoverer.setFailIfSendResultIsError(true);
                recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
                recoverer.accept(new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        "contract.events", 0, 0, "12", "broken-json"),
                        new InvalidEventException("bad JSON"));
                try (var consumer = new KafkaConsumer<String, String>(Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"))) {
                    consumer.assign(List.of(new TopicPartition("contract.events.DLT", 0)));
                    assertTrue(received(consumer, "contract.events.DLT",
                            record -> record.value().equals("broken-json")));
                }

                OccupantProjectionService rooms = mock(OccupantProjectionService.class);
                var processor = new ContractEventProcessor(new JsonMapper(), jdbc, rooms);
                UUID contractId = UUID.randomUUID();
                String signed = signed(contractId);
                doThrow(new IllegalStateException("domain failed")).when(rooms)
                        .apply(org.mockito.ArgumentMatchers.any());
                assertThrows(IllegalStateException.class,
                        () -> tx.executeWithoutResult(status -> processor.process(signed)));
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?",
                        Integer.class, contractId));
                reset(rooms);
                tx.executeWithoutResult(status -> processor.process(signed));
                tx.executeWithoutResult(status -> processor.process(signed));
                verify(rooms, times(1)).apply(org.mockito.ArgumentMatchers.any());
                assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?",
                        Integer.class, contractId));
            } finally {
                template.destroy();
                producerFactory.destroy();
            }
        }
    }

    private static String signed(UUID id) {
        return "{\"eventId\":\"" + id + "\",\"eventType\":\"ContractSigned\","
                + "\"version\":1,\"occurredAt\":\"2026-10-19T00:00:00Z\","
                + "\"payload\":{\"contractId\":7,\"roomId\":12,\"buildingId\":34,\"sourceRevision\":1,\"startDate\":\"2026-10-19\"}}";
    }

    private static boolean received(KafkaConsumer<String, String> consumer, String topic,
                                    Predicate<ConsumerRecord<String, String>> matches) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            for (var record : consumer.poll(Duration.ofMillis(500)).records(topic)) {
                if (matches.test(record)) return true;
            }
        }
        return false;
    }
}
