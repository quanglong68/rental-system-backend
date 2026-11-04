package com.rental.propertiesservice.features.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rental.common.constant.Headers;
import com.rental.propertiesservice.features.building.domain.Building;
import com.rental.propertiesservice.features.room.domain.Room;
import com.rental.propertiesservice.features.roomprice.domain.RoomPrice;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;

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
                SELECT b.name, b.address, b.ward_id, t.name, b.latitude, b.longitude,
                       b.lessor_name, b.lessor_date_of_birth, b.lessor_id_number, b.lessor_id_issue_date,
                       b.lessor_id_issue_place, b.lessor_phone, b.lessor_address
                FROM building b JOIN room_type t ON t.room_type_id = ?
                WHERE b.building_id = ?
                """, (rs, row) -> new RoomDetails(rs.getString(1), rs.getString(2),
                rs.getLong(3), rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6),
                rs.getString(7), rs.getObject(8, java.time.LocalDate.class), rs.getString(9),
                rs.getObject(10, java.time.LocalDate.class), rs.getString(11), rs.getString(12),
                rs.getString(13)),
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
                price, details.latitude(), details.longitude(),
                details.lessorName(), details.lessorDateOfBirth(), details.lessorIdNumber(),
                details.lessorIdIssueDate(), details.lessorIdIssuePlace(), details.lessorPhone(),
                details.lessorAddress()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void roomDeleted(Room room) {
        write("RoomDeleted", room.getId(), new RoomDeletedPayload(
                Long.toString(room.getId()), Long.toString(room.getBuildingId()), room.getRoomCode()));
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
                Long.toString(building.getWardId()), null, building.getLatitude(), building.getLongitude(),
                building.getBuildingStatus(), building.getLessorName(), building.getLessorDateOfBirth(),
                building.getLessorIdNumber(), building.getLessorIdIssueDate(), building.getLessorIdIssuePlace(),
                building.getLessorPhone(), building.getLessorAddress()));
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
            correlationId = request.getRequest().getHeader(Headers.X_CORRELATION_ID);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            envelope.put("correlationId", correlationId);
        }
        envelope.put("payload", payload);
        String jsonPayload;
        try {
            jsonPayload = json.writeValueAsString(envelope);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Không serialize được payload sự kiện", invalid);
        }
        jdbc.update("""
                INSERT INTO outbox_event (outbox_event_id, topic, event_key, event_type, payload)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """, eventId, topic, Long.toString(key), type, jsonPayload);
    }

    private record RoomDetails(String buildingName, String address, Long wardId,
                               String roomTypeName, BigDecimal latitude, BigDecimal longitude,
                               String lessorName, LocalDate lessorDateOfBirth, String lessorIdNumber,
                               LocalDate lessorIdIssueDate, String lessorIdIssuePlace,
                               String lessorPhone, String lessorAddress) {}
    private record RoomStatusPayload(String roomId, String buildingId, String buildingName,
                                     String address, String wardId, String districtId,
                                     String roomTypeId, String roomTypeName, String roomNumber,
                                     Integer maxCapacity, String status, String previousStatus,
                                     BigDecimal price, BigDecimal latitude, BigDecimal longitude,
                                     String lessorName, LocalDate lessorDateOfBirth, String lessorIdNumber,
                                     LocalDate lessorIdIssueDate, String lessorIdIssuePlace,
                                     String lessorPhone, String lessorAddress) {}
    private record RoomPricePayload(String roomId, BigDecimal price, LocalDate effectiveFrom) {}
    private record RoomDeletedPayload(String roomId, String buildingId, String roomNumber) {}
    private record BuildingPayload(String buildingId, String name, String address,
                                   String wardId, String districtId, BigDecimal latitude,
                                   BigDecimal longitude, String buildingStatus,
                                   String lessorName, LocalDate lessorDateOfBirth, String lessorIdNumber,
                                   LocalDate lessorIdIssueDate, String lessorIdIssuePlace,
                                   String lessorPhone, String lessorAddress) {}
}
