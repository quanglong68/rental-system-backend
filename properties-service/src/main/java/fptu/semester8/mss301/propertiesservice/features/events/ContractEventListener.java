package fptu.semester8.mss301.propertiesservice.features.events;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ContractEventListener {
    private static final Logger log = LoggerFactory.getLogger(ContractEventListener.class);
    private final ContractEventProcessor processor;
    private final ObjectMapper json;

    @KafkaListener(topics = "${property.kafka.contract-topic}")
    public void receive(String message) {
        try {
            processor.process(message);
        } catch (RuntimeException failure) {
            String eventId = "unknown";
            String eventType = "unknown";
            String aggregateId = "unknown";
            String correlationId = "unknown";
            try {
                var envelope = json.readTree(message);
                eventId = envelope.path("eventId").asString("unknown");
                eventType = envelope.path("eventType").asString("unknown");
                aggregateId = envelope.path("payload").path("roomId").asString("unknown");
                correlationId = envelope.path("correlationId").asString("unknown");
            } catch (Exception ignored) {
                // A malformed JSON record still reaches the error handler and DLT.
            }
            log.warn("Contract event failed eventId={} eventType={} aggregateId={} correlationId={}",
                    eventId, eventType, aggregateId, correlationId, failure);
            throw failure;
        }
    }
}
