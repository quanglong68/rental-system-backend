package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByRentalRequestId(Long rentalRequestId);

    /**
     * Same-row read used by BOTH the DRAFT update and signing, so the two serialize on one row
     * instead of racing (see {@code ContractService}). Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.id = :id")
    Optional<Contract> findByIdForUpdate(@Param("id") Long id);

    /**
     * Whether another ACTIVE contract of the same room overlaps the given term.
     * <p>
     * Boundaries are inclusive on both ends (spec 11.2: the contract range start_date..end_date is
     * counted inclusively, and Billing prorates by those days), so two contracts sharing a single
     * calendar day of the same room are an overlap. {@code end_date IS NULL} on an ACTIVE row cannot
     * happen through signing (signing rejects an incomplete term); it is treated as open-ended here
     * so such a row still blocks.
     */
    @Query("""
            select count(c) > 0
            from Contract c
            where c.roomId = :roomId
              and c.status = :status
              and c.id <> :excludeId
              and c.startDate <= :endDate
              and (c.endDate is null or c.endDate >= :startDate)
            """)
    boolean existsActiveOverlappingContract(@Param("roomId") Long roomId,
                                            @Param("status") ContractStatus status,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate,
                                            @Param("excludeId") Long excludeId);

    /**
     * Transaction-scoped PostgreSQL advisory lock on a caller-chosen logical key
     * ({@code "room:<id>"}, {@code "customer:<id>"}). Needed because the rules "one ACTIVE contract
     * per room and period" and "one customer in at most one ACTIVE contract" span <em>different</em>
     * contract rows: locking the row being signed cannot serialize two signings of different rows.
     * The lock is released at commit/rollback, so a crashed transaction cannot wedge the key.
     * <p>
     * ponytail: {@code hashtext} can collide two different keys, which costs a little extra
     * serialization but never correctness; move to two-int keys ({@code pg_advisory_xact_lock(cls,id)})
     * if contention ever shows up in metrics.
     */
    @Query(value = "select pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void lockNamedKey(@Param("key") String key);
}
