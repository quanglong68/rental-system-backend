package com.rental.contracttenant.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * Verifies the repository's query <em>definitions</em> without a database:
 * derived method names resolve against real entity properties, and the explicit JPQL translates
 * against the Hibernate metamodel of both entities (no JDBC connection needed, so this runs even
 * though Docker/Testcontainers is unavailable).
 * <p>
 * NOT verified here: generated SQL, execution, or returned rows. Those require a live PostgreSQL
 * and remain blocked in this environment.
 */
class ContractTenantRepositoryTest {

    private static StandardServiceRegistry registry;
    private static SessionFactoryImplementor sessionFactory;

    @BeforeAll
    static void buildOfflineHibernate() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        settings.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
        settings.put("hibernate.hbm2ddl.auto", "none");
        registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        sessionFactory = (SessionFactoryImplementor) new MetadataSources(registry)
                .addAnnotatedClass(Contract.class)
                .addAnnotatedClass(ContractTenant.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void close() {
        sessionFactory.close();
        registry.close();
    }

    @Test
    void derivedQueriesResolveAgainstRealEntityProperties() {
        // Throws PropertyReferenceException if any property name is wrong.
        assertThatCode(() -> {
            for (String name : List.of(
                    "findByContractIdOrderByJoinedAtAsc",
                    "findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc",
                    "findByContractIdAndCustomerIdAndLeftAtIsNull",
                    "existsByContractIdAndCustomerIdAndLeftAtIsNull")) {
                new PartTree(name, ContractTenant.class);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void currentOccupancyQueriesFilterOnLeftAtIsNull() {
        PartTree currentOccupants = new PartTree(
                "findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc", ContractTenant.class);
        PartTree membership = new PartTree(
                "existsByContractIdAndCustomerIdAndLeftAtIsNull", ContractTenant.class);

        // Each derived name must carry the IsNull keyword; without it these return tenants who left.
        assertThat(typesOf(currentOccupants)).containsExactly(
                Part.Type.SIMPLE_PROPERTY, Part.Type.IS_NULL);
        assertThat(typesOf(membership)).containsExactly(
                Part.Type.SIMPLE_PROPERTY, Part.Type.SIMPLE_PROPERTY, Part.Type.IS_NULL);
        assertThat(membership.isExistsProjection()).isTrue();
    }

    /** The @Query declared on the repository, read via reflection so the test can never drift from it. */
    private static String repositoryJpql() throws Exception {
        var method = ContractTenantRepository.class.getMethod(
                "existsCurrentTenantInContractWithStatus", Long.class, ContractStatus.class);
        assertThat(method.getReturnType()).isEqualTo(boolean.class);
        assertThat(method.getParameterAnnotations()[0])
                .extracting(Object::toString)
                .anySatisfy(s -> assertThat(s).contains("Param(\"customerId\")"));
        assertThat(method.getParameterAnnotations()[1])
                .extracting(Object::toString)
                .anySatisfy(s -> assertThat(s).contains("Param(\"status\")"));
        return method.getAnnotation(Query.class).value();
    }

    @Test
    void activeContractMembershipJpqlTranslatesAgainstTheMappedModel() throws Exception {
        String jpql = repositoryJpql();

        // Translation resolves every path (t.leftAt, c.status, the scalar t.contractId = c.id join)
        // against the metamodel, so an invented field or a bad join fails here.
        assertThatCode(() -> sessionFactory.getQueryEngine().getHqlTranslator().translate(jpql, Boolean.class))
                .doesNotThrowAnyException();
    }

    @Test
    void activeContractMembershipQueryFiltersEveryRequiredPredicate() throws Exception {
        // The status parameter is why this cannot be a derived query: the ACTIVE check lives on
        // Contract, joined through the scalar contractId, which the entity model has no association for.
        assertThat(repositoryJpql())
                .contains("t.customerId = :customerId")
                .contains("t.leftAt is null")
                .contains("c.id = t.contractId")
                .contains("c.status = :status");
    }

    private static List<Part.Type> typesOf(PartTree tree) {
        List<Part.Type> types = new ArrayList<>();
        tree.getParts().forEach(part -> types.add(part.getType()));
        return types;
    }

    // ---- Task 4: signing queries (definitions only; execution needs PostgreSQL) ----

    /** The @Query declared on ContractRepository, read via reflection so it cannot drift. */
    private static String contractJpql(String methodName) throws Exception {
        var method = ContractRepository.class.getMethod(methodName, Long.class, ContractStatus.class,
                java.time.LocalDate.class, java.time.LocalDate.class, Long.class);
        return method.getAnnotation(Query.class).value();
    }

    @Test
    void activeOverlapJpqlTranslatesAgainstTheMappedModel() throws Exception {
        String jpql = contractJpql("existsActiveOverlappingContract");

        assertThatCode(() -> sessionFactory.getQueryEngine().getHqlTranslator().translate(jpql, Boolean.class))
                .doesNotThrowAnyException();
        // Inclusive boundaries both ways (spec 11.2 counts the term inclusively): a contract that
        // ends on the new one's start_date still overlaps, which is why neither side is strict.
        assertThat(jpql)
                .contains("c.roomId = :roomId")
                .contains("c.status = :status")
                .contains("c.id <> :excludeId")
                .contains("c.startDate <= :endDate")
                .contains("c.endDate is null or c.endDate >= :startDate");
    }

    @Test
    void signingReadUsesAPessimisticWriteLock() throws Exception {
        var method = ContractRepository.class.getMethod("findByIdForUpdate", Long.class);

        assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        // Lock annotations only apply to a @Query-derived read; without it Spring silently ignores them.
        assertThat(method.getAnnotation(Query.class)).isNotNull();
    }

    @Test
    void advisoryLockIsADeclaredNativeQuery() throws Exception {
        var method = ContractRepository.class.getMethod("lockNamedKey", String.class);

        // Native, because PostgreSQL's transaction-scoped advisory locks have no JPQL equivalent.
        Query query = method.getAnnotation(Query.class);
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).contains("pg_advisory_xact_lock");
    }
}
