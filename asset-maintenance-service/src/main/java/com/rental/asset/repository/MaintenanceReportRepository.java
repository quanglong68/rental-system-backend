package com.rental.asset.repository;

import com.rental.asset.entity.MaintenanceReport;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MaintenanceReportRepository extends JpaRepository<MaintenanceReport, Long> {

    List<MaintenanceReport> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<MaintenanceReport> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, String status);

    @Query("select r.id from MaintenanceReport r where r.customerId = :customerId")
    List<Long> findIdsByCustomerId(Long customerId);

    Page<MaintenanceReport> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<MaintenanceReport> findByCustomerIdAndStatusOrderByCreatedAtDesc(
            Long customerId, String status, Pageable pageable);

    Page<MaintenanceReport> findByBuildingIdOrderByCreatedAtDesc(Long buildingId, Pageable pageable);

    Page<MaintenanceReport> findByBuildingIdAndStatusOrderByCreatedAtDesc(
            Long buildingId, String status, Pageable pageable);
}
