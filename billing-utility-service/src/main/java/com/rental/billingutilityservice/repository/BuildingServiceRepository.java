package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.BuildingService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildingServiceRepository extends JpaRepository<BuildingService, Long> {
    List<BuildingService> findByBuildingId(Long buildingId);
}
