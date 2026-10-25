package com.rental.contracttenant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Spec 5.3 consumer contract: auto-commit off, ack-mode RECORD, 3 retries one second apart, then
 * the record goes to {@code <source topic>.DLT}.
 */
class KafkaConfigTest {

    private final KafkaConfig config = new KafkaConfig();

    @Test
    void consumerFactoryDisablesAutoCommitAndStartsFromEarliest() {
        ConsumerFactory<String, String> factory = config.consumerFactory(new StandardEnvironment());

        assertThat(factory.getConfigurationProperties())
                .containsEntry("enable.auto.commit", false)
                .containsEntry("auto.offset.reset", "earliest")
                .containsEntry("group.id", "contract-tenant-service");
    }

    @Test
    void listenerFactoryUsesAckModeRecord() {
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);
        DefaultErrorHandler errorHandler = config.kafkaErrorHandler(mock(KafkaTemplate.class));

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    @Test
    void errorHandlerRetriesThreeTimesOneSecondApart() {
        BackOffExecution execution = KafkaConfig.RETRY_BACKOFF.start();

        assertThat(execution.nextBackOff()).isEqualTo(1000L);
        assertThat(execution.nextBackOff()).isEqualTo(1000L);
        assertThat(execution.nextBackOff()).isEqualTo(1000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void errorHandlerIsWiredWithTheDltRecoverer() {
        DefaultErrorHandler errorHandler = config.kafkaErrorHandler(mock(KafkaTemplate.class));

        assertThat(errorHandler).isNotNull();
        assertThat(KafkaConfig.deadLetterTarget(new ConsumerRecord<>("listing.events", 2, 0L, "k", "v")))
                .isEqualTo(new TopicPartition("listing.events.DLT", 2));
    }

    @Test
    void consumedTopicMatchesTheSpec() {
        assertThat(KafkaConfig.CONSUMED_TOPIC).isEqualTo("listing.events");
    }
}
