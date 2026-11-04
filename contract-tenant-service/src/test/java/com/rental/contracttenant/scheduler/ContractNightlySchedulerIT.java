package com.rental.contracttenant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
import com.rental.contracttenant.repository.ContractRepository;
import com.rental.contracttenant.repository.ContractTenantRepository;
import com.rental.contracttenant.repository.MoveOutRequestRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Goes through the real {@code @Scheduled} method against PostgreSQL instead of asserting on mocked
 * repositories: {@link ContractNightlyScheduler#processExpiredContracts()} is called directly so the
 * nightly ContractEnded flow (status, occupants, move-out requests, outbox) is proven immediately
 * without waiting for 00:10.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        // No broker here: the test only reads/writes the outbox table, so skip topic creation.
        "spring.kafka.admin.auto-create=false"
})
@Import(ContractNightlySchedulerIT.FixedClockConfiguration.class)
class ContractNightlySchedulerIT {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-10-22T18:00Z == 2026-10-23 01:00 in Asia/Ho_Chi_Minh. */
    private static final Instant NOW = Instant.parse("2026-10-22T18:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ContractNightlyScheduler scheduler;
    @Autowired
    private ContractRepository contracts;
    @Autowired
    private ContractTenantRepository tenants;
    @Autowired
    private MoveOutRequestRepository moveOutRequests;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    @Test
    void expiredContract_endsOccupancy_closesMoveOut_andPublishesContractEndedOnce() throws Exception {
        long roomId = uniqueRoomId();
        Contract expired = activeContract(roomId, TODAY.minusDays(3), null);
        // A follow-up contract for the same room, starting after the old one ends.
        activeContract(roomId, TODAY.plusYears(1), null);
        ContractTenant occupant = activeOccupant(expired.getId());
        MoveOutRequest openMoveOut = pendingMoveOut(expired.getId());

        scheduler.processExpiredContracts();

        Contract afterRun = contracts.findById(expired.getId()).orElseThrow();
        assertThat(afterRun.getStatus()).isEqualTo(ContractStatus.ENDED);
        assertThat(afterRun.getRevision()).isEqualTo(1);

        assertThat(tenants.findById(occupant.getId()).orElseThrow().getLeftAt())
                .isEqualTo(TODAY.minusDays(3));
        assertThat(moveOutRequests.findById(openMoveOut.getId()).orElseThrow().getStatus())
                .isEqualTo(MoveOutStatus.COMPLETED);

        List<Map<String, Object>> events = contractEndedEvents(expired.getId());
        assertThat(events).hasSize(1);
        JsonNode envelope = json.readTree((String) events.getFirst().get("payload"));
        assertThat(envelope.path("eventType").asText()).isEqualTo("ContractEnded");
        JsonNode payload = envelope.path("payload");
        assertThat(payload.path("contractId").asLong()).isEqualTo(expired.getId());
        assertThat(payload.path("roomId").asLong()).isEqualTo(roomId);
        assertThat(payload.path("status").asText()).isEqualTo("ENDED");
        assertThat(payload.path("endDate").asText()).isEqualTo(TODAY.minusDays(3).toString());
        assertThat(payload.path("sourceRevision").asLong()).isEqualTo(1);
        assertThat(payload.path("nextContractExists").asBoolean()).isTrue();
        assertThat(payload.path("tenantIds").get(0).asLong()).isEqualTo(occupant.getCustomerId());

        // The row lock + status guard must keep a second nightly run from emitting a duplicate event.
        scheduler.processExpiredContracts();

        assertThat(contractEndedEvents(expired.getId())).hasSize(1);
        assertThat(contracts.findById(expired.getId()).orElseThrow().getRevision()).isEqualTo(1);
    }

    @Test
    void expiredContractWithTerminationReason_becomesTerminated_withoutNextContract() throws Exception {
        long roomId = uniqueRoomId();
        Contract expired = activeContract(roomId, TODAY.minusDays(1), "Khach tra phong som");

        scheduler.processExpiredContracts();

        assertThat(contracts.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.TERMINATED);
        JsonNode payload = json.readTree((String) contractEndedEvents(expired.getId())
                .getFirst().get("payload")).path("payload");
        assertThat(payload.path("status").asText()).isEqualTo("TERMINATED");
        assertThat(payload.path("nextContractExists").asBoolean()).isFalse();
    }

    @Test
    void contractEndingToday_isNotEndedYet() {
        long roomId = uniqueRoomId();
        Contract endsToday = activeContract(roomId, TODAY, null);

        scheduler.processExpiredContracts();

        assertThat(contracts.findById(endsToday.getId()).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.ACTIVE);
        assertThat(contractEndedEvents(endsToday.getId())).isEmpty();
    }

    private static long uniqueRoomId() {
        return 900_000L + (System.nanoTime() % 99_999L);
    }

    private Contract activeContract(long roomId, LocalDate endDate, String terminationReason) {
        Contract contract = new Contract();
        contract.setRentalRequestId(System.nanoTime());
        contract.setRoomId(roomId);
        contract.setBuildingId(3L);
        contract.setCustomerId(77L);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(endDate.minusYears(1));
        contract.setEndDate(endDate);
        contract.setRentAmount(new BigDecimal("3500000"));
        contract.setTerminationReason(terminationReason);
        return contracts.save(contract);
    }

    private ContractTenant activeOccupant(Long contractId) {
        ContractTenant tenant = new ContractTenant();
        tenant.setContractId(contractId);
        tenant.setCustomerId(77L);
        tenant.setJoinedAt(TODAY.minusYears(1));
        tenant.setRepresentative(true);
        return tenants.save(tenant);
    }

    private MoveOutRequest pendingMoveOut(Long contractId) {
        MoveOutRequest request = new MoveOutRequest();
        request.setContractId(contractId);
        request.setRequestedBy(77L);
        request.setRequestDate(TODAY.minusDays(5));
        request.setExpectedMoveOutDate(TODAY.minusDays(2));
        request.setReason("Ve que");
        request.setStatus(MoveOutStatus.PENDING);
        return moveOutRequests.save(request);
    }

    private List<Map<String, Object>> contractEndedEvents(Long contractId) {
        return jdbc.queryForList(
                "SELECT event_type, payload::text AS payload FROM outbox_event "
                        + "WHERE event_key = ? AND event_type = 'ContractEnded' ORDER BY sequence_no",
                contractId.toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(NOW, BUSINESS_ZONE);
        }
    }
}
