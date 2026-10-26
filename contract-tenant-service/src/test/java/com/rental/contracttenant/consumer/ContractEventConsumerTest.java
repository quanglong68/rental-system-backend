package com.rental.contracttenant.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.contracttenant.entity.ProcessedEvent;
import com.rental.contracttenant.repository.ProcessedEventRepository;
import com.rental.contracttenant.service.ContractService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ContractEventConsumerTest {

    @Mock
    private ContractService contractService;
    @Mock
    private ProcessedEventRepository processedEvents;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ContractEventConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        consumer = new ContractEventConsumer(contractService, processedEvents, transactionManager, objectMapper);
    }

    private static String rentalRequestAccepted(UUID eventId) {
        return """
            {
                "eventId": "%s",
                "eventType": "RentalRequestAccepted",
                "payload": {
                    "rentalRequestId": 1,
                    "roomId": 10,
                    "buildingId": 100,
                    "customerId": 200,
                    "price": "5000000",
                    "moveInDate": "2026-11-01"
                }
            }
            """.formatted(eventId);
    }

    @Test
    void consume_rentalRequestAccepted_notProcessed_createsDraft() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.existsById(eventId)).thenReturn(false);

        consumer.consume(rentalRequestAccepted(eventId));

        verify(contractService).createDraft(any());
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void consume_rentalRequestAccepted_alreadyProcessed_skips() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.existsById(eventId)).thenReturn(true);

        consumer.consume(rentalRequestAccepted(eventId));

        verify(contractService, never()).createDraft(any());
        verify(processedEvents, never()).save(any());
    }

    @Test
    void consume_roommateApplicationAccepted_notProcessed_processesRoommate() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.existsById(eventId)).thenReturn(false);

        String json = """
            {
                "eventId": "%s",
                "eventType": "RoommateApplicationAccepted",
                "payload": {
                    "applicationId": 5,
                    "postId": 15,
                    "roomId": 10,
                    "buildingId": 100,
                    "ownerCustomerId": 200,
                    "applicantCustomerId": 300
                }
            }
            """.formatted(eventId);

        consumer.consume(json);

        verify(contractService).processRoommateAccepted(any());
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    /** Spec 5.3: event types this service does not handle are skipped, not treated as failures. */
    @Test
    void consume_unknownEventType_isIgnoredWithoutWritingAnything() {
        consumer.consume("""
            {"eventId": "%s", "eventType": "ListingPublished", "payload": {}}
            """.formatted(UUID.randomUUID()));

        verify(contractService, never()).createDraft(any());
        verify(contractService, never()).processRoommateAccepted(any());
        verify(processedEvents, never()).save(any());
    }

    /**
     * A malformed envelope must be thrown, not swallowed, so KafkaConfig's error handler retries and
     * finally publishes to listing.events.DLT (spec 5.3).
     */
    @Test
    void consume_malformedJson_throwsSoTheErrorHandlerCanRetryAndDlt() {
        assertThatThrownBy(() -> consumer.consume("{not json"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(processedEvents, never()).save(any());
    }

    @Test
    void consume_missingEventId_throwsInsteadOfSkipping() {
        assertThatThrownBy(() -> consumer.consume("""
                {"eventType": "RentalRequestAccepted", "payload": {}}
                """))
                .isInstanceOf(IllegalArgumentException.class);
        verify(contractService, never()).createDraft(any());
    }

    @Test
    void consume_missingEventType_throwsInsteadOfSkipping() {
        assertThatThrownBy(() -> consumer.consume("""
                {"eventId": "8a1b2c3d-0000-0000-0000-000000000000", "payload": {}}
                """))
                .isInstanceOf(IllegalArgumentException.class);
        verify(contractService, never()).createDraft(any());
    }

    @Test
    void consume_incompletePayload_throwsInsteadOfWritingAPartialDraft() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.existsById(eventId)).thenReturn(false);

        assertThatThrownBy(() -> consumer.consume("""
                {"eventId": "%s", "eventType": "RentalRequestAccepted",
                 "payload": {"rentalRequestId": 1, "roomId": 10}}
                """.formatted(eventId)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(contractService, never()).createDraft(any());
        verify(processedEvents, never()).save(any());
    }
}
