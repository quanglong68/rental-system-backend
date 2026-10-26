package com.rental.asset.repository;

import com.rental.asset.entity.MaintenanceRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    Optional<MaintenanceRecord> findByMaintenanceRequestId(Long maintenanceRequestId);
}
