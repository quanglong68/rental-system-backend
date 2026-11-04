package com.rental.propertiesservice.features.building.infrastructure.persistence.repository;

import com.rental.propertiesservice.features.building.domain.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface SpringDataBuildingRepository extends JpaRepository<Building, Long> {
    Optional<Building> findByBuildingIdAndDeletedFalse(Long buildingId);

    Optional<Building> findByBuildingId(Long buildingId);

    Page<Building> findAllByDeletedFalse(Pageable pageable);

    Page<Building> findAllByWardIdAndDeletedFalse(Long wardId, Pageable pageable);

    Page<Building> findAllByBuildingIdInAndDeletedFalse(Collection<Long> buildingIds, Pageable pageable);

    Page<Building> findAllByWardIdAndBuildingIdInAndDeletedFalse(Long wardId, Collection<Long> buildingIds,
                                                                Pageable pageable);
}
