package com.rental.asset.repository;

import com.rental.asset.entity.MaintenanceSchedule;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Long> {

    List<MaintenanceSchedule> findByBuildingIdAndActiveTrue(Long buildingId);

    List<MaintenanceSchedule> findByStaffIdAndActiveTrue(Long staffId);

    List<MaintenanceSchedule> findByActiveTrueAndNextDateBetween(LocalDate fromDate, LocalDate toDate);
}
