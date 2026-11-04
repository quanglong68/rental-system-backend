package com.rental.propertiesservice.features.building.application.port.out;

import com.rental.propertiesservice.features.building.domain.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Optional;

public interface BuildingRepository {
    Building save(Building building);

    Optional<Building> findActiveById(long buildingId);

    Optional<Building> findById(long buildingId);

    Page<Building> findActive(Pageable pageable);

    Page<Building> findActiveByWardId(long wardId, Pageable pageable);

    /** STAFF scope: only the assigned buildings. */
    Page<Building> findActiveIn(Collection<Long> buildingIds, Pageable pageable);

    Page<Building> findActiveByWardIdIn(long wardId, Collection<Long> buildingIds, Pageable pageable);
}
