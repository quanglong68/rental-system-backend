package fptu.semester8.mss301.propertiesservice.features.events;

import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.OccupantProjectionService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractEventProcessorTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OccupantProjectionService projection = mock(OccupantProjectionService.class);
    ContractEventProcessor processor = new ContractEventProcessor(new JsonMapper(), jdbc, projection);
    @BeforeEach void setup() { when(jdbc.update(anyString(), any(UUID.class))).thenReturn(1); }
    @Test void addedAcceptsStringIdsWithoutPrecisionLoss() {
        processor.process(event());
        verify(projection).apply(argThat(e -> e.assignmentId() == 898785309339156482L && e.customerId() == 77 && e.revision() == 1));
    }
    @Test void duplicateDoesNotMutateAgain() {
        when(jdbc.update(anyString(), any(UUID.class))).thenReturn(1, 0);
        String message = event();
        processor.process(message);
        processor.process(message);
        verify(projection, times(1)).apply(any());
    }
    @Test void malformedPayloadDoesNotRecordMarker() {
        assertThrows(InvalidEventException.class, () -> processor.process(event().replace("\"sourceRevision\":1", "\"sourceRevision\":0")));
        verifyNoInteractions(jdbc, projection);
    }
    @Test void missingRevisionIsQuarantinedRatherThanUsingTimestampOrdering() {
        assertThrows(InvalidEventException.class, () -> processor.process(event().replace(",\"sourceRevision\":1", "")));
        verifyNoInteractions(jdbc, projection);
    }
    @Test void invalidDateAndAssignmentIdFailBeforeMarker() {
        assertThrows(InvalidEventException.class, () -> processor.process(event().replace("2026-10-21", "2026-19-99")));
        assertThrows(InvalidEventException.class, () -> processor.process(event().replace("898785309339156482", "9223372036854775808")));
        verifyNoInteractions(jdbc, projection);
    }
    @Test void unrelatedEventsIgnored() {
        processor.process("{\"eventType\":\"InvoiceIssued\"}");
        verifyNoInteractions(jdbc, projection);
    }
    @Test void failuresPropagateToKafkaRetryHandler() {
        doThrow(new IllegalStateException("missing prerequisite")).when(projection).apply(any());
        assertThrows(IllegalStateException.class, () -> processor.process(event()));
    }
    private String event() {
        return """
                {"eventId":"%s","eventType":"TenantAdded","version":1,"occurredAt":"2026-10-21T00:00:00Z",
                 "payload":{"contractId":7,"roomId":12,"contractTenantId":"898785309339156482",
                 "customerId":77,"joinedAt":"2026-10-21","sourceRevision":1}}
                """.formatted(UUID.randomUUID());
    }
}
