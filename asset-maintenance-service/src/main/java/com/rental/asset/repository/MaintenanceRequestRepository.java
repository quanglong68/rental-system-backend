package com.rental.asset.repository;

import com.rental.asset.entity.MaintenanceRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    List<MaintenanceRequest> findByReportIdIn(List<Long> reportIds);

    Page<MaintenanceRequest> findByStaffIdOrderByCreatedAtDesc(Long staffId, Pageable pageable);

    Page<MaintenanceRequest> findByBuildingIdOrderByCreatedAtDesc(Long buildingId, Pageable pageable);
}
