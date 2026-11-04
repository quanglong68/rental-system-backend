package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.ContractRenewalRequest;
import com.rental.contracttenant.entity.RenewalRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRenewalRequestRepository extends JpaRepository<ContractRenewalRequest, Long> {

    boolean existsByContractIdAndStatus(Long contractId, RenewalRequestStatus status);

    List<ContractRenewalRequest> findByRequestedByOrderByCreatedAtDesc(Long requestedBy);

    Optional<ContractRenewalRequest> findFirstByContractIdOrderByCreatedAtDesc(Long contractId);

    Optional<ContractRenewalRequest> findFirstByContractIdAndStatusOrderByCreatedAtDesc(
            Long contractId, RenewalRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ContractRenewalRequest r where r.id = :id")
    Optional<ContractRenewalRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT r FROM ContractRenewalRequest r, Contract c WHERE r.contractId = c.id " +
           "AND (:buildingId IS NULL OR c.buildingId = :buildingId) " +
           "AND (:allowedBuildings IS NULL OR c.buildingId IN :allowedBuildings) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.createdAt DESC")
    Page<ContractRenewalRequest> findByFilters(
            @Param("buildingId") Long buildingId,
            @Param("allowedBuildings") Collection<Long> allowedBuildings,
            @Param("status") RenewalRequestStatus status,
            Pageable pageable);
}
