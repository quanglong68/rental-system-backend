package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractTenantRepository extends JpaRepository<ContractTenant, Long> {

    /** Full occupancy history of a contract, oldest first. Rows are never deleted, so this includes tenants who left. */
    List<ContractTenant> findByContractIdOrderByJoinedAtAsc(Long contractId);

    /** Current occupants only: left_at IS NULL. This is row-level presence, not contract status. */
    List<ContractTenant> findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(Long contractId);

    /** One customer's current occupancy of a specific contract, if any. */
    Optional<ContractTenant> findByContractIdAndCustomerIdAndLeftAtIsNull(Long contractId, Long customerId);

    /** Same as above without loading the row; for validation before inserting a new tenant. */
    boolean existsByContractIdAndCustomerIdAndLeftAtIsNull(Long contractId, Long customerId);

    /**
     * Whether a customer currently occupies a contract in the given status — {@code ACTIVE} for the
     * spec rule "một khách không được thuộc hai hợp đồng ACTIVE cùng lúc".
     * <p>
     * A current row ({@code leftAt IS NULL}) is NOT by itself proof of an ACTIVE contract, so this
     * deliberately joins {@link com.rental.contracttenant.entity.Contract} for the status. The scalar
     * {@code contractId} is joined explicitly because the entities have no JPA relationship.
     * <p>
     * LIMITATION: nothing in the schema keeps {@code contract_tenant.left_at} in step with
     * {@code contract.status}. A contract that ended without every occupant being closed out, or a
     * tenant row created under a DRAFT contract, is still reported here per its contract status.
     * Callers must not read this as "the occupant is verified present in the room today".
     */
    @Query("""
            select count(t) > 0
            from ContractTenant t
            where t.customerId = :customerId
              and t.leftAt is null
              and exists (
                  select 1 from Contract c
                  where c.id = t.contractId and c.status = :status
              )
            """)
    boolean existsCurrentTenantInContractWithStatus(@Param("customerId") Long customerId,
                                                    @Param("status") ContractStatus status);
}
