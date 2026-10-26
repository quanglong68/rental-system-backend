package fptu.semester8.mss301.propertiesservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {
    @Test
    void createsErrorHandlerWithoutConnectingToKafka() {
        assertInstanceOf(DefaultErrorHandler.class,
                new KafkaConfig().kafkaDefaultErrorHandler(mock(KafkaTemplate.class), 1000, 3, 10));
    }
}
