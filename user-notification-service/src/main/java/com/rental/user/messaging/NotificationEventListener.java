package com.rental.user.messaging;

import com.rental.user.service.NotificationEventProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j @Component
public class NotificationEventListener {
    private final NotificationEventProcessor processor;
    public NotificationEventListener(NotificationEventProcessor processor) { this.processor = processor; }

    @KafkaListener(topics = {"auth.events", "property.events", "listing.events", "contract.events",
            "billing.events", "asset.events"})
    public void onEvent(String eventJson) throws Exception {
        processor.process(eventJson);
    }
}
