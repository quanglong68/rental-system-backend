package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.MeterReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {
    Optional<MeterReading> findByMeter_IdAndMonthAndYear(Long meterId, Integer month, Integer year);

    List<MeterReading> findByMeter_IdAndYear(Long meterId, Integer year);

    Optional<MeterReading> findTopByMeter_IdAndYearAndMonthLessThanOrderByYearDescMonthDesc(Long meterId, Integer year,
            Integer month);

    @Query(value = """
            select r from MeterReading r join r.meter m
            where (:meterId is null or m.id = :meterId)
              and (:roomId is null or m.roomId = :roomId)
              and (:buildingId is null or m.buildingId = :buildingId)
              and (:month is null or r.month = :month)
              and (:year is null or r.year = :year)
            """,
            countQuery = """
            select count(r) from MeterReading r join r.meter m
            where (:meterId is null or m.id = :meterId)
              and (:roomId is null or m.roomId = :roomId)
              and (:buildingId is null or m.buildingId = :buildingId)
              and (:month is null or r.month = :month)
              and (:year is null or r.year = :year)
            """)
    Page<MeterReading> search(Long meterId, Long roomId, Long buildingId, Integer month, Integer year,
            Pageable pageable);

    boolean existsByMeter_IdAndMonthAndYear(Long meterId, Integer month, Integer year);
}
