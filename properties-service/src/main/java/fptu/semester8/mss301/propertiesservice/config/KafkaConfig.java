package fptu.semester8.mss301.propertiesservice.config;

import fptu.semester8.mss301.propertiesservice.features.events.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class KafkaConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);
    @Bean
    DefaultErrorHandler kafkaDefaultErrorHandler(KafkaTemplate<String, String> kafka,
            @Value("${property.kafka.retry-interval-ms}") long retryIntervalMs,
            @Value("${property.kafka.retry-count}") long retryCount,
            @Value("${property.kafka.send-timeout-seconds}") long sendTimeoutSeconds) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, failure) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(sendTimeoutSeconds));
        var handler = new DefaultErrorHandler((record, failure) -> {
            log.warn("Moving failed Kafka record to DLT topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset(), failure);
            recoverer.accept(record, failure);
        },
                new FixedBackOff(retryIntervalMs, retryCount));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
