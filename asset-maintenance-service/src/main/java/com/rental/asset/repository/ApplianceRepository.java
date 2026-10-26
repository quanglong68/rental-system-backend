package com.rental.asset.repository;

import com.rental.asset.entity.Appliance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceRepository extends JpaRepository<Appliance, Long> {

    Page<Appliance> findByRoomId(Long roomId, Pageable pageable);

    Page<Appliance> findByBuildingId(Long buildingId, Pageable pageable);

    Page<Appliance> findByBuildingIdAndStatus(Long buildingId, String status, Pageable pageable);
}
