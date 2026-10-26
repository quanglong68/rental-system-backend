package fptu.semester8.mss301.propertiesservice.features.events;

import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.OccupantProjectionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ContractEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(ContractEventProcessor.class);
    private static final Set<String> HANDLED = Set.of("TenantAdded", "TenantRemoved", "ContractEnded",
            "ContractSigned", "MoveOutApproved", "ContractRenewed");
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final OccupantProjectionService projection;

    @Transactional
    public void process(String message) {
        JsonNode envelope;
        try {
            envelope = json.readTree(message);
        } catch (Exception invalid) {
            throw new InvalidEventException("Invalid contract event JSON", invalid);
        }
        if (envelope == null || !envelope.isObject())
            throw new InvalidEventException("Contract event envelope must be an object");
        String type = requiredText(envelope, "eventType");
        if (!HANDLED.contains(type)) return;
        UUID eventId;
        try {
            eventId = UUID.fromString(requiredText(envelope, "eventId"));
            Instant.parse(requiredText(envelope, "occurredAt"));
        } catch (RuntimeException invalid) {
            throw new InvalidEventException("Invalid eventId or occurredAt", invalid);
        }
        if (!envelope.path("version").isIntegralNumber() || !envelope.path("version").canConvertToInt()
                || envelope.path("version").asInt() != 1)
            throw new InvalidEventException("Unsupported contract event schema version");
        JsonNode p = envelope.path("payload");
        if (!p.isObject()) throw new InvalidEventException("Missing contract event payload");
        long contractId = requiredLong(p, "contractId");
        long roomId = requiredLong(p, "roomId");
        long revision = requiredLong(p, "sourceRevision");
        Long buildingId = p.has("buildingId") ? requiredLong(p, "buildingId") : null;
        boolean assignmentEvent = type.equals("TenantAdded") || type.equals("TenantRemoved");
        Long assignmentId = assignmentEvent ? requiredLong(p, "contractTenantId") : null;
        Long customerId = assignmentEvent ? requiredLong(p, "customerId") : null;
        String dateField = switch (type) {
            case "TenantAdded" -> "joinedAt";
            case "TenantRemoved" -> "leftAt";
            case "ContractSigned" -> "startDate";
            case "MoveOutApproved" -> "expectedMoveOutDate";
            case "ContractRenewed" -> "newEndDate";
            default -> "endDate";
        };
        LocalDate date;
        try {
            date = LocalDate.parse(requiredText(p, dateField));
        } catch (RuntimeException invalid) {
            throw new InvalidEventException("Invalid " + dateField, invalid);
        }
        boolean next = false;
        if (type.equals("ContractEnded")) {
            if (!p.path("nextContractExists").isBoolean())
                throw new InvalidEventException("Missing nextContractExists");
            next = p.path("nextContractExists").asBoolean();
        }
        int inserted = jdbc.update("""
                INSERT INTO processed_event(event_id,processed_at) VALUES (?,CURRENT_TIMESTAMP)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId);
        if (inserted == 0) return;
        projection.apply(new OccupantProjectionService.Change(eventId, type, contractId, roomId, buildingId,
                assignmentId, customerId, date, revision, next));
        log.info("Processed contract eventId={} eventType={} contractId={} roomId={} sourceRevision={} correlationId={}",
                eventId, type, contractId, roomId, revision, envelope.path("correlationId").asString(""));
    }

    private static String requiredText(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isTextual() || value.asString().isBlank())
            throw new InvalidEventException("Missing or invalid " + field);
        return value.asString();
    }

    private static long requiredLong(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isIntegralNumber() && value.canConvertToLong() && value.asLong() > 0) return value.asLong();
        if (value.isTextual()) {
            try {
                long id = Long.parseLong(value.asString());
                if (id > 0) return id;
            } catch (NumberFormatException ignored) {
            }
        }
        throw new InvalidEventException("Missing or invalid " + field);
    }
}
