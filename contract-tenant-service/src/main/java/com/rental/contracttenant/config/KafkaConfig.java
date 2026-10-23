package com.rental.contracttenant.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer wiring per spec 5.3: group.id = service name, enable.auto.commit=false, ack-mode RECORD,
 * auto.offset.reset=earliest. A failing record is retried 3 times, 1 second apart, then published to
 * {@code <topic>.DLT}. Only the topics this service actually consumes get a DLT.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String CONSUMED_TOPIC = "listing.events";

    @Bean
    ConsumerFactory<String, String> consumerFactory(Environment environment) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "contract-tenant-service");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /** Spec 5.3: 3 retries, 1 second apart. */
    static final FixedBackOff RETRY_BACKOFF = new FixedBackOff(1000L, 3L);

    /** Spec 5.3: a record that still fails goes to {@code <source topic>.DLT}. */
    static TopicPartition deadLetterTarget(ConsumerRecord<?, ?> record) {
        return new TopicPartition(record.topic() + ".DLT", record.partition());
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(template, (record, ex) -> deadLetterTarget(record));
        // FixedBackOff(interval, maxAttempts): 3 retries one second apart, then the recoverer sends the
        // record to <topic>.DLT, so a permanently bad message never blocks the partition.
        return new DefaultErrorHandler(recoverer, RETRY_BACKOFF);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    NewTopic listingEventsTopic() {
        return new NewTopic(CONSUMED_TOPIC, 3, (short) 1);
    }

    @Bean
    NewTopic listingEventsDlt() {
        return new NewTopic(CONSUMED_TOPIC + ".DLT", 3, (short) 1);
    }
}
