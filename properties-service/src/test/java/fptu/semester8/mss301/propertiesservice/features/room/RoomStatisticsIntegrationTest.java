package fptu.semester8.mss301.propertiesservice.features.room;

import fptu.semester8.mss301.propertiesservice.support.PostgresIntegrationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.time.ZoneId;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomStatisticsIntegrationTest extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void setup() {
        // UTC is still the previous day; the business date must use Vietnam time.
        when(clock.instant()).thenReturn(Instant.parse("2026-10-20T18:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        jdbc.execute("TRUNCATE room_occupant,occupant_projection_checkpoint,processed_event,outbox_event,room,building,ward,province CASCADE");
        jdbc.update("INSERT INTO province(province_id,code,name) VALUES (1,'T','Test')");
        jdbc.update("INSERT INTO ward(ward_id,province_id,code,name) VALUES (1,1,'T','Test')");
        for (long id = 1; id <= 3; id++) {
            jdbc.update("INSERT INTO building(building_id,province_id,ward_id,name,address,latitude,longitude,is_deleted) VALUES (?,1,1,'Test','Test',10,106,?)", id, id == 3);
        }
    }

    private void room(long id, long building, String status, int capacity) {
        jdbc.update("INSERT INTO room(room_id,building_id,room_type_id,room_number,max_capacity,status,area,bedroom_count,bathroom_count,base_price) VALUES (?,?,123456789012345601,?,?,?,20,0,1,0)", id, building, "R" + id, capacity, status);
    }

    private void occupant(long id, long room, long user, String start, String end, String status) {
        jdbc.update("INSERT INTO room_occupant(room_occupant_id,room_id,user_id,move_in_date,move_out_date,room_occupant_status) VALUES (?,?,?,CAST(? AS date),CAST(? AS date),?)", id, room, user, start, end, status);
    }

    @Test void countsDistinctPeopleInclusiveDatesAndEachRoomOnlyOnce() throws Exception {
        room(11, 1, "VACANT", 2); // Valid empty room; future and departed occupants excluded.
        room(12, 1, "OCCUPIED", 2); // One distinct person, not operating.
        room(13, 1, "UPCOMING_VACANT", 2); // Two current people, valid.
        room(14, 1, "VACANT", 1); // Mismatch + over capacity + missing fields: one update.
        room(15, 1, "OCCUPIED", 2); // Empty but occupied.
        room(16, 1, "MAINTENANCE", 2); // Empty maintenance is valid.
        room(17, 2, "VACANT", 2); // Missing required information.
        room(18, 3, "OCCUPIED", 1); // Deleted building excluded.
        occupant(1, 11, 1, "2026-10-22", null, "ACTIVE");
        occupant(2, 11, 2, "2026-10-01", "2026-10-20", "ENDED");
        occupant(3, 12, 3, "2026-10-21", null, "ACTIVE");
        occupant(4, 12, 3, "2026-10-21", null, "ACTIVE");
        occupant(5, 13, 4, "2026-10-21", null, "ACTIVE");
        occupant(6, 13, 5, "2026-10-01", "2026-10-21", "ENDED");
        occupant(7, 14, 6, "2026-10-21", null, "ACTIVE");
        occupant(8, 14, 7, "2026-10-21", null, "ACTIVE");
        jdbc.update("UPDATE room SET area=NULL,base_price=NULL WHERE room_id IN (14,17)");
        assertStatistics("", 7, 2, 3);
        assertStatistics("?buildingId=1", 6, 2, 2);
        assertStatistics("?buildingId=2", 1, 0, 1);
        assertStatistics("?buildingId=3", 0, 0, 0);
        assertStatistics("?buildingId=999", 0, 0, 0);
    }

    @Test void flagsMaintenanceWithPeopleEmptyUpcomingAndBlankCode() throws Exception {
        room(1, 1, "MAINTENANCE", 2);
        occupant(1, 1, 1, "2026-10-21", null, "ACTIVE");
        room(2, 1, "UPCOMING_VACANT", 2);
        room(3, 1, "VACANT", 2);
        jdbc.update("UPDATE room SET room_number='   ' WHERE room_id=3");
        assertStatistics("", 3, 0, 3);
    }

    @Test void emptyDatabaseReturnsZeroAndValidatesFilterAndAuthorization() throws Exception {
        assertStatistics("", 0, 0, 0);
        for (String invalid : new String[]{"0", "-1", "abc", "9223372036854775808"}) {
            mvc.perform(get("/api/v1/rooms/statistics?buildingId=" + invalid)
                .header("X-User-Id", "1").header("X-User-Type", "STAFF").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/rooms/statistics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/rooms/statistics")
                .header("X-User-Id", "1").header("X-User-Type", "CUSTOMER").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    private void assertStatistics(String query, long total, long operating, long updates) throws Exception {
        mvc.perform(get("/api/v1/rooms/statistics" + query)
                .header("X-User-Id", "1").header("X-User-Type", "STAFF").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRooms").value(total))
                .andExpect(jsonPath("$.data.operatingRooms").value(operating))
                .andExpect(jsonPath("$.data.roomsNeedingUpdate").value(updates))
                .andExpect(jsonPath("$.data.businessDate").value("2026-10-21"));
    }
}
