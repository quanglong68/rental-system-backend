package com.rental.contracttenant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Executes the move-out repository queries against a real PostgreSQL, which the offline Hibernate
 * tests cannot do. The previous {@code ... status IN ('PENDING', 'APPROVED')} JPQL compared an enum
 * column to string literals; this test proves the replacement (a derived query binding a collection
 * of {@link MoveOutStatus}) actually runs, and that V7's {@code updated_at} columns are in place.
 *
 * <p>Skipped unless {@code CONTRACT_TEST_DB_URL} (plus the matching user/password env vars) points at
 * a PostgreSQL database this test may migrate and write to. Testcontainers is preferred when Docker
 * is available; with Docker down, point the variables at a local server:
 * <pre>
 * CONTRACT_TEST_DB_URL=jdbc:postgresql://localhost:5432/contract_tenant_verify \
 * CONTRACT_TEST_DB_USER=postgres CONTRACT_TEST_DB_PASSWORD=... \
 * mvnw -pl contract-tenant-service test -Dtest=MoveOutRequestRepositoryDbTest
 * </pre>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false",
        "eureka.client.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "CONTRACT_TEST_DB_URL", matches = ".+")
class MoveOutRequestRepositoryDbTest {

    @Autowired
    private MoveOutRequestRepository moveOutRequests;

    @Autowired
    private TestEntityManager em;

    /** Runs the real migrations, then points the slice at the same database. */
    @DynamicPropertySource
    static void migrate(DynamicPropertyRegistry registry) {
        String url = System.getenv("CONTRACT_TEST_DB_URL");
        if (url == null || url.isBlank()) {
            return; // class-level @EnabledIfEnvironmentVariable skips the test when this is unset.
        }
        String user = System.getenv().getOrDefault("CONTRACT_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("CONTRACT_TEST_DB_PASSWORD", "");

        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .migrate();

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void findOpenByContractId_returnsPendingAndApprovedOnly() {
        Long contractId = persistContract();

        MoveOutRequest pending = persistMoveOut(contractId, MoveOutStatus.PENDING);
        MoveOutRequest approved = persistMoveOut(contractId, MoveOutStatus.APPROVED);
        persistMoveOut(contractId, MoveOutStatus.REJECTED);
        persistMoveOut(contractId, MoveOutStatus.COMPLETED);
        em.flush();
        em.clear();

        List<MoveOutRequest> open = moveOutRequests.findByContractIdAndStatusIn(
                contractId, List.of(MoveOutStatus.PENDING, MoveOutStatus.APPROVED));

        assertThat(open).extracting(MoveOutRequest::getId)
                .containsExactlyInAnyOrder(pending.getId(), approved.getId());
    }

    @Test
    void findByContractIdAndStatusIn_emptyStatusCollection_returnsNothing() {
        Long contractId = persistContract();
        persistMoveOut(contractId, MoveOutStatus.PENDING);
        em.flush();
        em.clear();

        assertThat(moveOutRequests.findByContractIdAndStatusIn(contractId, List.of())).isEmpty();
    }

    /** Spec convention: every table carries created_at and updated_at, maintained by Hibernate. */
    @Test
    void businessTablesHaveCreatedAtAndUpdatedAtColumns() {
        Map<String, Boolean> expected = Map.of(
                "contract", true, "contract_tenant", true, "move_out_request", true);

        for (String table : expected.keySet()) {
            assertThat(columnsOf(table))
                    .as("%s columns", table)
                    .contains("created_at", "updated_at");
        }
    }

    @Test
    void insertedRowGetsUpdatedAtPopulated() {
        Long contractId = persistContract();
        MoveOutRequest saved = persistMoveOut(contractId, MoveOutStatus.PENDING);
        em.flush();
        em.clear();

        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private List<String> columnsOf(String table) {
        @SuppressWarnings("unchecked")
        List<String> columns = em.getEntityManager()
                .createNativeQuery("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = :table")
                .setParameter("table", table)
                .getResultList();
        return columns;
    }

    private Long persistContract() {
        Contract contract = new Contract();
        contract.setRentalRequestId(System.nanoTime());
        contract.setRoomId(12L);
        contract.setBuildingId(3L);
        contract.setCustomerId(77L);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDate.of(2026, 11, 1));
        contract.setEndDate(LocalDate.of(2027, 11, 1));
        contract.setRentAmount(new BigDecimal("3500000"));
        return em.persistAndGetId(contract, Long.class);
    }

    private MoveOutRequest persistMoveOut(Long contractId, MoveOutStatus status) {
        MoveOutRequest request = new MoveOutRequest();
        request.setContractId(contractId);
        request.setRequestedBy(77L);
        request.setRequestDate(LocalDate.of(2026, 10, 22));
        request.setExpectedMoveOutDate(LocalDate.of(2026, 12, 1));
        request.setReason("Ve que");
        request.setStatus(status);
        return em.persistAndFlush(request);
    }
}
