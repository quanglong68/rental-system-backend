package com.rental.listingbooking.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rental.common.constant.Headers;
import com.rental.common.event.EventEnvelope;
import com.rental.common.security.HeaderAuthenticationFilter;
import com.rental.listingbooking.entity.OutboxEvent;
import com.rental.listingbooking.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class ListingEventPublisher {

    public static final String LISTING_EVENTS_TOPIC = "listing.events";

    public static final String EVENT_RENTAL_REQUEST_CREATED = "RentalRequestCreated";
    public static final String EVENT_RENTAL_REQUEST_ACCEPTED = "RentalRequestAccepted";
    public static final String EVENT_RENTAL_REQUEST_REJECTED = "RentalRequestRejected";
    public static final String EVENT_APPOINTMENT_SCHEDULED = "AppointmentScheduled";
    public static final String EVENT_APPOINTMENT_STATUS_CHANGED = "AppointmentStatusChanged";
    public static final String EVENT_ROOMMATE_APP_CREATED = "RoommateApplicationCreated";
    public static final String EVENT_ROOMMATE_APP_ACCEPTED = "RoommateApplicationAccepted";
    public static final String EVENT_ROOMMATE_APP_REJECTED = "RoommateApplicationRejected";

    private final OutboxEventRepository outbox;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void publishRentalRequestCreated(RentalRequestCreatedPayload payload) {
        saveOutboxEvent(EVENT_RENTAL_REQUEST_CREATED, String.valueOf(payload.rentalRequestId()), payload);
    }

    public void publishRentalRequestAccepted(RentalRequestAcceptedPayload payload) {
        saveOutboxEvent(EVENT_RENTAL_REQUEST_ACCEPTED, String.valueOf(payload.rentalRequestId()), payload);
    }

    public void publishRentalRequestRejected(RentalRequestRejectedPayload payload) {
        saveOutboxEvent(EVENT_RENTAL_REQUEST_REJECTED, String.valueOf(payload.rentalRequestId()), payload);
    }

    public void publishAppointmentScheduled(AppointmentCreatedPayload payload) {
        saveOutboxEvent(EVENT_APPOINTMENT_SCHEDULED, String.valueOf(payload.appointmentId()), payload);
    }

    public void publishAppointmentStatusChanged(AppointmentStatusChangedPayload payload) {
        saveOutboxEvent(EVENT_APPOINTMENT_STATUS_CHANGED, String.valueOf(payload.appointmentId()), payload);
    }

    public void publishRoommateApplicationCreated(RoommateApplicationCreatedPayload payload) {
        saveOutboxEvent(EVENT_ROOMMATE_APP_CREATED, String.valueOf(payload.applicationId()), payload);
    }

    public void publishRoommateApplicationAccepted(RoommateApplicationAcceptedPayload payload) {
        saveOutboxEvent(EVENT_ROOMMATE_APP_ACCEPTED, String.valueOf(payload.applicationId()), payload);
    }

    public void publishRoommateApplicationRejected(RoommateApplicationRejectedPayload payload) {
        saveOutboxEvent(EVENT_ROOMMATE_APP_REJECTED, String.valueOf(payload.applicationId()), payload);
    }

    private <T> void saveOutboxEvent(String eventType, String eventKey, T payload) {
        EventEnvelope<T> envelope = EventEnvelope.of(eventType, payload, correlationId());
        OutboxEvent event = new OutboxEvent();
        event.setId(envelope.getEventId());
        event.setTopic(LISTING_EVENTS_TOPIC);
        event.setEventKey(eventKey);
        event.setEventType(eventType);
        event.setCreatedAt(Instant.now());
        try {
            event.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Không thể serialize " + eventType, impossible);
        }
        outbox.save(event);
    }

    private static String correlationId() {
        String fromMdc = MDC.get(HeaderAuthenticationFilter.MDC_CORRELATION_ID);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromHeader = null;
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
                fromHeader = servlet.getRequest().getHeader(Headers.X_CORRELATION_ID);
            }
        } catch (Exception ignored) {
        }
        return fromHeader != null && !fromHeader.isBlank() ? fromHeader : UUID.randomUUID().toString();
    }
}
