package com.rental.contracttenant.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.boot.MetadataSources;
import org.hibernate.mapping.Column;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the JPA mapping of {@link ContractTenant} at metadata-build time. Hibernate resolves the
 * dialect from the explicit setting, so no database connection (and therefore no Docker /
 * Testcontainers) is required. This does NOT verify the V2 migration itself: Flyway is only
 * exercised by an integration test against PostgreSQL, which is blocked in this environment.
 */
class ContractTenantMappingTest {

    private static StandardServiceRegistry registry;
    private static org.hibernate.boot.Metadata metadata;

    @BeforeAll
    static void buildMetadata() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        settings.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
        settings.put("hibernate.hbm2ddl.auto", "none");
        registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        metadata = new MetadataSources(registry).addAnnotatedClass(ContractTenant.class).buildMetadata();
    }

    @AfterAll
    static void closeRegistry() {
        registry.close();
    }

    @Test
    void mapsToContractTenantTableWithSnakeCaseColumns() {
        var table = metadata.getEntityBinding(ContractTenant.class.getName()).getTable();

        assertThat(table.getName()).isEqualTo("contract_tenant");
        assertThat(table.getColumns()).extracting(Column::getName).containsExactlyInAnyOrder(
                "id", "contract_id", "customer_id", "joined_at", "left_at", "is_representative", "created_at");
    }

    @Test
    void usesIdentityIdsAndHistorySafeDates() {
        var binding = metadata.getEntityBinding(ContractTenant.class.getName());

        assertThat(binding.getIdentifier().getType().getReturnedClass()).isEqualTo(Long.class);
        assertThat(binding.getProperty("joinedAt").getType().getReturnedClass()).isEqualTo(LocalDate.class);
        assertThat(binding.getProperty("leftAt").getType().getReturnedClass()).isEqualTo(LocalDate.class);
        assertThat(binding.getProperty("createdAt").getType().getReturnedClass()).isEqualTo(Instant.class);
    }

    @Test
    void nullableLeavingDateIsTheOnlyOptionalColumn() {
        var binding = metadata.getEntityBinding(ContractTenant.class.getName());

        assertThat(binding.getProperty("leftAt").isOptional()).isTrue();
        assertThat(binding.getProperty("contractId").isOptional()).isFalse();
        assertThat(binding.getProperty("customerId").isOptional()).isFalse();
        assertThat(binding.getProperty("joinedAt").isOptional()).isFalse();
    }

    @Test
    void storesRepresentativeFlagAsBooleanColumn() {
        var binding = metadata.getEntityBinding(ContractTenant.class.getName());

        assertThat(binding.getProperty("isRepresentative").getType().getReturnedClass()).isEqualTo(Boolean.class);
        assertThat(binding.getProperty("isRepresentative").getColumns().get(0).getName())
                .isEqualTo("is_representative");
    }
}
