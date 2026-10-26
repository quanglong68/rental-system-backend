package fptu.semester8.mss301.propertiesservice.features.roomoccupant;

import fptu.semester8.mss301.propertiesservice.support.PostgresIntegrationSupport;
import fptu.semester8.mss301.propertiesservice.features.events.*;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.*;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.port.out.RoomOccupantRepository;
import fptu.semester8.mss301.propertiesservice.features.room.application.RoomOccupancyStatusPolicy;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomOccupantIntegrationTest extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired ContractEventProcessor processor;
    @Autowired RoomOccupantService reads;
    @Autowired RoomOccupantRepository occupants;
    @Autowired RoomOccupancyStatusPolicy policy;
    @Autowired MockMvc mvc;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-21T05:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        jdbc.execute("TRUNCATE room_occupant,occupant_projection_checkpoint,processed_event,outbox_event,room,building,ward,province CASCADE");
        jdbc.update("INSERT INTO province(province_id,code,name) VALUES (1,'T','Test')");
        jdbc.update("INSERT INTO ward(ward_id,province_id,code,name) VALUES (1,1,'T','Test')");
        jdbc.update("INSERT INTO building(building_id,province_id,ward_id,name,address,latitude,longitude) VALUES (34,1,1,'Test','Test',10,106)");
        jdbc.update("INSERT INTO room(room_id,building_id,room_type_id,room_number,max_capacity,status) VALUES (12,34,123456789012345601,'A1',2,'VACANT'),(13,34,123456789012345601,'A2',2,'VACANT')");
    }

    @Test void buildingBecomesInactiveOnlyAfterLastResidentsInclusiveDepartureDate() {
        add(5001, 77, 1, "2026-10-20");
        remove(5001, 77, 2, "2026-10-21");
        assertEquals(fptu.semester8.mss301.propertiesservice.features.building.domain.BuildingStatus.ACTIVE.getValue(),
                jdbc.queryForObject("SELECT building_status FROM building WHERE building_id=34", String.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T05:00:00Z"));
        policy.refresh(12);
        assertEquals(fptu.semester8.mss301.propertiesservice.features.building.domain.BuildingStatus.INACTIVE.getValue(),
                jdbc.queryForObject("SELECT building_status FROM building WHERE building_id=34", String.class));
    }

    @Test void buildingStaysActiveWhenAnotherRoomHasResidents() {
        add(5001, 77, 1, "2026-10-20");
        processor.process(event("TenantAdded", 1, assignment(6001, 88, "joinedAt", "2026-10-20"))
                .replace("\"contractId\":7", "\"contractId\":8").replace("\"roomId\":12", "\"roomId\":13"));
        remove(5001, 77, 2, "2026-10-20");
        assertEquals(fptu.semester8.mss301.propertiesservice.features.building.domain.BuildingStatus.ACTIVE.getValue(),
                jdbc.queryForObject("SELECT building_status FROM building WHERE building_id=34", String.class));
    }

    @Test void inclusiveDatesHistoryRejoinAndOverCapacity() {
        assertEquals(0, reads.summary(12, null).currentOccupants());
        add(5001, 77, 1, "2026-10-20");
        add(5002, 78, 2, "2026-10-21");
        assertEquals(0, reads.summary(12, null).availableCapacity());
        add(5003, 79, 3, "2026-10-21");
        var full = reads.summary(12, null);
        assertTrue(full.overCapacity());
        assertEquals(-1, full.availableCapacity());
        remove(5001, 77, 4, "2026-10-21");
        assertEquals(3, reads.summary(12, null).currentOccupants()); // Departure date is inclusive.
        assertEquals(2, reads.summary(12, LocalDate.parse("2026-10-22")).currentOccupants());
        add(5004, 77, 5, "2026-10-23");
        assertEquals(3, reads.summary(12, LocalDate.parse("2026-10-23")).currentOccupants());
        assertEquals(4, reads.history(12, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(2, reads.byUser(77, PageRequest.of(0, 20)).getTotalElements());
        assertEquals("OCCUPIED", roomStatus());
    }

    @Test void duplicateStaleAndOldAssignmentRemovalCannotEndRejoinedUser() {
        String add = event("TenantAdded", 1, assignment(5001, 77, "joinedAt", "2026-10-01"));
        processor.process(add);
        processor.process(add);
        remove(5001, 77, 2, "2026-10-10");
        add(5002, 77, 3, "2026-10-11");
        processor.process(event("TenantRemoved", 2, assignment(5001, 77, "leftAt", "2026-10-10"))); // new ID, stale revision
        assertEquals(2, reads.history(12, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(1, reads.summary(12, null).currentOccupants());
        assertNull(occupants.byAssignment(5002).orElseThrow().getMoveOutDate());
    }

    @Test void contractEndKeepsOtherContractAndMaintenanceHistory() {
        add(5001, 77, 1, "2026-10-01");
        processor.process(event("TenantAdded", 1, assignment(6001, 88, "joinedAt", "2026-10-01")).replace("\"contractId\":7", "\"contractId\":8"));
        jdbc.update("UPDATE room SET status='MAINTENANCE' WHERE room_id=12");
        processor.process(event("ContractEnded", 2, "\"endDate\":\"2026-10-20\",\"nextContractExists\":false"));
        assertEquals("MAINTENANCE", roomStatus());
        assertEquals(1, reads.summary(12, null).currentOccupants());
        assertEquals(2, reads.history(12, PageRequest.of(0, 20)).getTotalElements());
        assertNull(occupants.byAssignment(6001).orElseThrow().getMoveOutDate());
        assertThrows(InvalidEventException.class, () -> add(5003, 77, 3, "2026-10-21"));
    }

    @Test void finalDepartureNeverInfersVacancyFromIncompleteRoomState() {
        add(5001, 77, 1, "2026-10-01");
        processor.process(event("ContractEnded", 2, "\"endDate\":\"2026-10-20\",\"nextContractExists\":false"));
        assertEquals(0, reads.summary(12, null).currentOccupants());
        assertEquals("OCCUPIED", roomStatus());
    }

    @Test void gapsMissingAssignmentsAndRoomsRollBackMarkersAndCheckpoint() {
        String missing = event("TenantRemoved", 1, assignment(5001, 77, "leftAt", "2026-10-20"));
        assertThrows(IllegalStateException.class, () -> processor.process(missing));
        assertEquals(0, count("processed_event"));
        assertEquals(0, count("occupant_projection_checkpoint"));
        String gap = event("TenantAdded", 2, assignment(5001, 77, "joinedAt", "2026-10-20"));
        assertThrows(IllegalStateException.class, () -> processor.process(gap));
        assertEquals(0, count("processed_event"));
        add(5002, 78, 1, "2026-10-01");
        processor.process(gap); // retry after prerequisite
        String unknown = event("TenantAdded", 3, assignment(5003, 79, "joinedAt", "2026-10-20")).replace("\"roomId\":12", "\"roomId\":999");
        assertThrows(IllegalStateException.class, () -> processor.process(unknown));
        assertEquals(2, count("processed_event"));
    }

    @Test void futureArrivalAndApprovedMoveOutHaveDateAwareStatus() {
        add(5001, 77, 1, "2026-10-22");
        assertEquals(0, reads.summary(12, null).currentOccupants());
        assertEquals("VACANT", roomStatus());
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T05:00:00Z"));
        policy.refresh(12);
        assertEquals("OCCUPIED", roomStatus());
        processor.process(event("MoveOutApproved", 2, "\"expectedMoveOutDate\":\"2026-10-30\""));
        assertEquals("UPCOMING_VACANT", roomStatus());
        processor.process(event("ContractSigned", 1, "\"startDate\":\"2026-10-22\""));
        assertEquals("UPCOMING_VACANT", roomStatus()); // Older event cannot undo approved departure.
        processor.process(event("ContractRenewed", 3, "\"newEndDate\":\"2026-12-31\""));
        assertEquals("OCCUPIED", roomStatus());
    }

    @Test void tenantAddedNeverOverwritesMaintenanceAndSignedDoesNotInventOccupants() {
        jdbc.update("UPDATE room SET status='MAINTENANCE' WHERE room_id=12");
        processor.process(event("ContractSigned", 1, "\"startDate\":\"2026-10-21\""));
        assertEquals(0, count("room_occupant"));
        add(5001, 77, 2, "2026-10-21");
        assertEquals("MAINTENANCE", roomStatus());
    }

    @Test void replayAndConcurrentDuplicateDeliveriesConverge() throws Exception {
        String message = event("TenantAdded", 1, assignment(5001, 77, "joinedAt", "2026-10-21"));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Void> delivery = () -> { barrier.await(); processor.process(message); return null; };
            var a = pool.submit(delivery); var b = pool.submit(delivery);
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        }
        assertEquals(1, count("room_occupant"));
        assertEquals(1, count("processed_event"));
        assertEquals(1, count("outbox_event"));
        processor.process(message);
        assertEquals(1, count("room_occupant"));
    }

    @Test void invalidDepartureAndDatabaseFailureDoNotAcknowledgeEvents() {
        add(5001, 77, 1, "2026-10-21");
        assertThrows(InvalidEventException.class, () -> remove(5001, 77, 2, "2026-10-20"));
        assertEquals(1, count("processed_event"));
        jdbc.execute("ALTER TABLE room_occupant ADD CONSTRAINT test_reject_user CHECK(user_id <> 99)");
        try {
            assertThrows(RuntimeException.class, () -> add(5002, 99, 2, "2026-10-21"));
            assertEquals(1, count("processed_event"));
            assertEquals(1L, jdbc.queryForObject("SELECT source_revision FROM occupant_projection_checkpoint WHERE contract_id=7", Long.class));
        } finally { jdbc.execute("ALTER TABLE room_occupant DROP CONSTRAINT test_reject_user"); }
    }

    @Test void sampleFixturesReplayWithoutLosingHistory() throws Exception {
        for (int pass = 0; pass < 2; pass++) {
            for (String name : List.of("tenant-added", "tenant-removed", "contract-ended")) {
                try (var input = new org.springframework.core.io.ClassPathResource("events/" + name + ".json").getInputStream()) {
                    processor.process(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        assertEquals(1, count("room_occupant"));
        assertEquals(3, count("processed_event"));
        assertEquals(0, reads.summary(12, null).currentOccupants());
        assertEquals(LocalDate.parse("2026-10-20"), occupants.byAssignment(5001).orElseThrow().getMoveOutDate());
    }

    @Test void databaseConstraintsPreserveAssignmentsAndRejectInvalidReferences() {
        add(5001, 77, 1, "2026-10-21");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO room_occupant(room_occupant_id,room_id,user_id,contract_id,contract_tenant_id,source_revision,move_in_date)
                VALUES (900,12,77,7,5001,2,'2026-10-21')
                """));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO room_occupant(room_occupant_id,room_id,user_id,contract_id,contract_tenant_id,source_revision,move_in_date)
                VALUES (900,999,77,7,5002,2,'2026-10-21')
                """));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO room_occupant(room_occupant_id,room_id,user_id,contract_id,contract_tenant_id,source_revision,move_in_date,move_out_date)
                VALUES (900,12,77,7,5002,2,'2026-10-21','2026-10-20')
                """));
    }

    @Test void restSecurityPaginationDtosAndNoWriteEndpoints() throws Exception {
        add(5001, 77, 1, "2026-10-21");
        mvc.perform(get("/api/v1/rooms/12/occupants")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/rooms/12/occupants").header("X-User-Id", "77").header("X-User-Type", "CUSTOMER").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/rooms/12/occupants").header("X-User-Id", "1").header("X-User-Type", "STAFF").header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isForbidden());
        mvc.perform(admin(get("/api/v1/rooms/12/occupants?size=1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].userId").value("77"))
                .andExpect(jsonPath("$.data.content[0].contractTenantId").value("5001"))
                .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"));
        mvc.perform(admin(get("/api/v1/rooms/12/occupants/history?page=1&size=1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(admin(get("/api/v1/rooms/12/occupancy-summary"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentOccupants").value(1));
        mvc.perform(admin(get("/api/v1/rooms/999/occupants"))).andExpect(status().isNotFound());
        mvc.perform(admin(get("/api/v1/rooms/12/occupants?size=101"))).andExpect(status().isBadRequest());
        long id = occupants.byAssignment(5001).orElseThrow().getId();
        mvc.perform(admin(get("/api/v1/rooms/12/occupants/" + id))).andExpect(status().isOk());
        mvc.perform(admin(get("/api/v1/rooms/13/occupants/" + id))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/rooms/occupancies/users/77").header("X-User-Id", "77").header("X-User-Type", "CUSTOMER").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/rooms/occupancies/users/78").header("X-User-Id", "77").header("X-User-Type", "CUSTOMER").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
        mvc.perform(admin(post("/api/v1/rooms/12/occupants").content("{}"))).andExpect(status().isForbidden());
        mvc.perform(admin(delete("/api/v1/rooms/12/occupants/" + id))).andExpect(status().isForbidden());
        jdbc.update("UPDATE building SET is_deleted=true WHERE building_id=34");
        mvc.perform(admin(get("/api/v1/rooms/12/occupants"))).andExpect(status().isNotFound());
        assertEquals(0, reads.byUser(77, PageRequest.of(0, 20)).getTotalElements());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder admin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", "1").header("X-User-Type", "ADMIN").header("X-User-Roles", "ADMIN");
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private String roomStatus() { return jdbc.queryForObject("SELECT status FROM room WHERE room_id=12", String.class); }
    private void add(long assignment, long user, long rev, String date) { processor.process(event("TenantAdded", rev, assignment(assignment,user,"joinedAt",date))); }
    private void remove(long assignment, long user, long rev, String date) { processor.process(event("TenantRemoved", rev, assignment(assignment,user,"leftAt",date))); }
    private static String assignment(long id, long user, String dateField, String date) {
        return "\"contractTenantId\":" + id + ",\"customerId\":" + user + ",\"" + dateField + "\":\"" + date + "\"";
    }
    private String event(String type, long rev, String fields) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + type
                + "\",\"version\":1,\"occurredAt\":\"2026-10-21T05:00:00Z\",\"payload\":{\"contractId\":7,\"roomId\":12,\"buildingId\":34,\"sourceRevision\":"
                + rev + "," + fields + "}}";
    }
}
