package fptu.semester8.mss301.propertiesservice.features.events;

import fptu.semester8.mss301.propertiesservice.features.building.domain.Building;
import fptu.semester8.mss301.propertiesservice.features.room.domain.Room;
import fptu.semester8.mss301.propertiesservice.features.roomprice.domain.RoomPrice;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.LinkedHashMap;

/** Stores the documented v1 envelope in the same transaction as the domain change. */
@Component
public class PropertyEventPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String topic;

    public PropertyEventPublisher(JdbcTemplate jdbc, ObjectMapper json,
                                  @Value("${property.kafka.property-topic}") String topic) {
        this.jdbc = jdbc;
        this.json = json;
        this.topic = topic;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void roomStatusChanged(Room room, String previousStatus) {
        var details = jdbc.queryForObject("""
                SELECT b.name, b.address, b.ward_id, t.name, b.latitude, b.longitude
                FROM building b JOIN room_type t ON t.room_type_id = ?
                WHERE b.building_id = ?
                """, (rs, row) -> new RoomDetails(rs.getString(1), rs.getString(2),
                rs.getLong(3), rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6)),
                room.getRoomTypeId(), room.getBuildingId());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        BigDecimal price = jdbc.query("""
                SELECT price FROM room_price
                WHERE room_id = ? AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                """, ps -> {
            ps.setLong(1, room.getId());
            ps.setObject(2, today);
            ps.setObject(3, today);
        }, rs -> rs.next() ? rs.getBigDecimal(1) : room.getBasePrice());
        write("RoomStatusChanged", room.getId(), new RoomStatusPayload(
                Long.toString(room.getId()), Long.toString(room.getBuildingId()), details.buildingName(), details.address(),
                Long.toString(details.wardId()), null, Long.toString(room.getRoomTypeId()), details.roomTypeName(),
                room.getRoomCode(), room.getMaxCapacity(), room.getStatus(), previousStatus,
                price, details.latitude(), details.longitude()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void roomPriceChanged(RoomPrice price) {
        write("RoomPriceChanged", price.getRoomId(),
                new RoomPricePayload(Long.toString(price.getRoomId()), price.getPrice(), price.getEffectiveFrom()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void buildingUpdated(Building building) {
        write("BuildingUpdated", building.getBuildingId(), new BuildingPayload(
                Long.toString(building.getBuildingId()), building.getName(), building.getAddress(),
                Long.toString(building.getWardId()), null, building.getLatitude(), building.getLongitude(), building.getBuildingStatus()));
    }

    private void write(String type, long key, Object payload) {
        UUID eventId = UUID.randomUUID();
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", type);
        envelope.put("version", 1);
        envelope.put("occurredAt", Instant.now().truncatedTo(ChronoUnit.SECONDS));
        String correlationId = MDC.get("correlationId");
        if ((correlationId == null || correlationId.isBlank())
                && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
            correlationId = request.getRequest().getHeader("X-Correlation-Id");
        }
        if (correlationId != null && !correlationId.isBlank()) {
            envelope.put("correlationId", correlationId);
        }
        envelope.put("payload", payload);
        jdbc.update("""
                INSERT INTO outbox_event (outbox_event_id, topic, event_key, event_type, payload)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """, eventId, topic, Long.toString(key), type, json.writeValueAsString(envelope));
    }

    private record RoomDetails(String buildingName, String address, Long wardId,
                               String roomTypeName, BigDecimal latitude, BigDecimal longitude) {}
    private record RoomStatusPayload(String roomId, String buildingId, String buildingName,
                                     String address, String wardId, String districtId,
                                     String roomTypeId, String roomTypeName, String roomNumber,
                                     Integer maxCapacity, String status, String previousStatus,
                                     BigDecimal price, BigDecimal latitude, BigDecimal longitude) {}
    private record RoomPricePayload(String roomId, BigDecimal price, LocalDate effectiveFrom) {}
    private record BuildingPayload(String buildingId, String name, String address,
                                   String wardId, String districtId, BigDecimal latitude,
                                   BigDecimal longitude, String buildingStatus) {}
}
