package fptu.semester8.mss301.propertiesservice.features.events;

import fptu.semester8.mss301.propertiesservice.features.building.domain.Building;
import fptu.semester8.mss301.propertiesservice.features.room.domain.Room;
import fptu.semester8.mss301.propertiesservice.features.roomprice.domain.RoomPrice;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PropertyEventPublisherTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JsonMapper json = new JsonMapper();
    private final PropertyEventPublisher publisher = new PropertyEventPublisher(jdbc, json, "property.events");

    @Test
    void buildingUpdatedUsesBuildingKeyAndDocumentedPayload() {
        publisher.buildingUpdated(new Building(898785309339156480L, "North", "12 Main", 7L, 8L,
                new BigDecimal("10.0"), new BigDecimal("106.0"), null, Instant.now()));
        JsonNode event = capturedEvent("898785309339156480", "BuildingUpdated");
        assertEquals("898785309339156480", event.path("payload").path("buildingId").asString());
        assertTrue(event.path("payload").path("buildingId").isTextual());
        assertEquals("North", event.path("payload").path("name").asString());
        assertEquals("12 Main", event.path("payload").path("address").asString());
        assertEquals("7", event.path("payload").path("wardId").asString());
        assertTrue(event.path("payload").path("wardId").isTextual());
    }

    @Test
    void roomPriceChangedUsesRoomKeyAndEffectiveDate() {
        publisher.roomPriceChanged(new RoomPrice(11L, 12L, new BigDecimal("5000000"),
                LocalDate.of(2030, 1, 1), null, Instant.now()));
        JsonNode event = capturedEvent("12", "RoomPriceChanged");
        assertEquals("12", event.path("payload").path("roomId").asString());
        assertTrue(event.path("payload").path("price").isNumber());
        assertEquals("5000000", event.path("payload").path("price").asString());
        assertEquals("2030-01-01", event.path("payload").path("effectiveFrom").asString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void roomStatusChangedUsesRoomKeyAndSnapshot() throws Exception {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(9L), eq(44L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString(1)).thenReturn("North");
                    when(rs.getString(2)).thenReturn("12 Main");
                    when(rs.getLong(3)).thenReturn(7L);
                    when(rs.getString(4)).thenReturn("Studio");
                    when(rs.getBigDecimal(5)).thenReturn(new BigDecimal("10.0"));
                    when(rs.getBigDecimal(6)).thenReturn(new BigDecimal("106.0"));
                    return mapper.mapRow(rs, 0);
                });
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(false);
                    return extractor.extractData(rs);
                });
        Room room = new Room(12L, 44L, 9L, "A101", 2, "OCCUPIED", null, null,
                null, new BigDecimal("5000000"), Instant.now());
        publisher.roomStatusChanged(room, "VACANT");
        JsonNode event = capturedEvent("12", "RoomStatusChanged");
        assertEquals("OCCUPIED", event.path("payload").path("status").asString());
        assertEquals("VACANT", event.path("payload").path("previousStatus").asString());
        assertEquals("44", event.path("payload").path("buildingId").asString());
        assertTrue(event.path("payload").path("buildingId").isTextual());
        assertEquals("A101", event.path("payload").path("roomNumber").asString());
        assertEquals("Studio", event.path("payload").path("roomTypeName").asString());
    }

    private JsonNode capturedEvent(String key, String type) {
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), any(), eq("property.events"), eq(key), eq(type), payload.capture());
        JsonNode event = json.readTree(payload.getValue());
        assertEquals(type, event.path("eventType").asString());
        assertEquals(1, event.path("version").asInt());
        assertTrue(event.path("eventId").isTextual());
        assertTrue(event.path("occurredAt").isTextual());
        return event;
    }
}
