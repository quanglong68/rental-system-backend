package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.SignatureStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByRentalRequestId(Long rentalRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.id = :id")
    Optional<Contract> findByIdForUpdate(@Param("id") Long id);

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

    @Query(value = "select pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void lockNamedKey(@Param("key") String key);

    @Query("""
            select c from Contract c
            where (:buildingId is null or c.buildingId = :buildingId)
              and (:roomId is null or c.roomId = :roomId)
              and (:status is null or c.status = :status)
              and (:allowedBuildings is null or c.buildingId in :allowedBuildings)
            """)
    Page<Contract> findByFilters(@Param("buildingId") Long buildingId,
                                 @Param("roomId") Long roomId,
                                 @Param("status") ContractStatus status,
                                 @Param("allowedBuildings") Collection<Long> allowedBuildings,
                                 Pageable pageable);

    /**
     * Contracts a customer can see in "hợp đồng của tôi": either they are still an occupant, or they
     * are the named customer of a draft that is waiting for their e-signature (occupant rows only
     * exist after both parties sign).
     */
    @Query("""
            select c from Contract c
            where c.customerId = :customerId
               or c.id in (
                   select t.contractId from ContractTenant t
                   where t.customerId = :customerId
                     and t.leftAt is null
               )
            order by c.createdAt desc
            """)
    List<Contract> findContractsForCustomer(@Param("customerId") Long customerId);

    @Query("""
            select c from Contract c
            where c.status = :status
              and (c.customerId = :customerId
                   or c.id in (
                       select t.contractId from ContractTenant t
                       where t.customerId = :customerId
                         and t.leftAt is null
                   ))
            order by c.startDate desc
            """)
    List<Contract> findActiveForCustomer(@Param("customerId") Long customerId,
                                         @Param("status") ContractStatus status);

    @Query("""
            select c from Contract c
            where c.startDate <= :monthEnd
              and (c.endDate is null or c.endDate >= :monthStart)
              and c.status in (:statuses)
            """)
    List<Contract> findContractsEffectiveInMonth(@Param("monthStart") LocalDate monthStart,
                                                 @Param("monthEnd") LocalDate monthEnd,
                                                 @Param("statuses") Collection<ContractStatus> statuses);

    @Query("""
            select c from Contract c
            where c.status = :status
              and c.endDate < :today
            """)
    List<Contract> findExpiredActiveContracts(@Param("status") ContractStatus status,
                                              @Param("today") LocalDate today);

    @Query("""
            select count(c) > 0
            from Contract c
            where c.roomId = :roomId
              and c.status = :status
              and c.startDate > :afterDate
            """)
    boolean existsNextActiveContract(@Param("roomId") Long roomId,
                                     @Param("status") ContractStatus status,
                                     @Param("afterDate") LocalDate afterDate);

    Optional<Contract> findFirstByRoomIdAndStatusOrderByStartDateDesc(Long roomId, ContractStatus status);

    @Query("""
            select c from Contract c
            where c.signatureStatus in :statuses
              and c.signatureDeadline is not null
              and c.signatureDeadline < :now
            """)
    List<Contract> findExpiredSignatureContracts(@Param("statuses") Collection<SignatureStatus> statuses,
                                                  @Param("now") Instant now);

    @Query("""
            select c from Contract c
            where c.status = :status
              and c.endDate between :from and :to
            """)
    List<Contract> findByStatusAndEndDateBetween(@Param("status") ContractStatus status,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    /** Hop dong da kich hoat va co ngay chuyen vao dung ngay chi dinh (quet nhac ban giao). */
    List<Contract> findByStatusAndStartDate(ContractStatus status, LocalDate startDate);

    Optional<Contract> findFirstByRenewedFromContractIdOrderByCreatedAtDesc(Long renewedFromContractId);

    Optional<Contract> findByRenewalRequestId(Long renewalRequestId);

    boolean existsByRenewedFromContractId(Long renewedFromContractId);
}
