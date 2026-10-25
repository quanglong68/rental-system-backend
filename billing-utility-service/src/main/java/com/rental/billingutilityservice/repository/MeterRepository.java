package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.Meter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeterRepository extends JpaRepository<Meter, Long> {
    Optional<Meter> findByRoomIdAndUtilityType_Id(Long roomId, Long utilityTypeId);

    Optional<Meter> findByRoomIdAndUtilityType_IdAndIsActiveTrue(Long roomId, Long utilityTypeId);

    List<Meter> findByRoomId(Long roomId);

    List<Meter> findByBuildingId(Long buildingId);

    List<Meter> findByRoomIdAndIsActiveTrue(Long roomId);

    List<Meter> findByBuildingIdAndIsActiveTrue(Long buildingId);
}
