package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
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
import org.springframework.stereotype.Repository;

@Repository
public interface MoveOutRequestRepository extends JpaRepository<MoveOutRequest, Long> {

    boolean existsByContractIdAndStatus(Long contractId, MoveOutStatus status);

    List<MoveOutRequest> findByRequestedByOrderByCreatedAtDesc(Long requestedBy);

    /** Locks the request row so concurrent approve/reject serialise on it, then re-read status. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MoveOutRequest m WHERE m.id = :id")
    Optional<MoveOutRequest> findByIdForUpdate(@Param("id") Long id);

    /** Derived query: the status set is bound as enum parameters, no string-literal comparison. */
    List<MoveOutRequest> findByContractIdAndStatusIn(Long contractId, Collection<MoveOutStatus> statuses);

    @Query("SELECT m FROM MoveOutRequest m, Contract c WHERE m.contractId = c.id " +
           "AND (:buildingId IS NULL OR c.buildingId = :buildingId) " +
           "AND (:allowedBuildings IS NULL OR c.buildingId IN :allowedBuildings) " +
           "AND (:status IS NULL OR m.status = :status)")
    Page<MoveOutRequest> findByFilters(
            @Param("buildingId") Long buildingId,
            @Param("allowedBuildings") Collection<Long> allowedBuildings,
            @Param("status") MoveOutStatus status,
            Pageable pageable);

    List<MoveOutRequest> findByContractIdAndStatus(Long contractId, MoveOutStatus status);
}
