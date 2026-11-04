package com.rental.asset.repository;

import com.rental.asset.entity.MaintenanceRequest;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    List<MaintenanceRequest> findByReportIdIn(List<Long> reportIds);

    Page<MaintenanceRequest> findByReportIdIn(List<Long> reportIds, Pageable pageable);

    Page<MaintenanceRequest> findByStaffIdOrderByCreatedAtDesc(Long staffId, Pageable pageable);

    Page<MaintenanceRequest> findByBuildingIdOrderByCreatedAtDesc(Long buildingId, Pageable pageable);

    @Query("""
            select r from MaintenanceRequest r
            where (:staffId is null or r.staffId = :staffId)
              and (:status is null or r.status = :status)
              and (:priority is null or r.priority = :priority)
            """)
    Page<MaintenanceRequest> searchByStaff(Long staffId, String status, String priority, Pageable pageable);

    @Query("""
            select r from MaintenanceRequest r
            where (:buildingId is null or r.buildingId = :buildingId)
              and (:staffId is null or r.staffId = :staffId)
              and (:status is null or r.status = :status)
              and (:priority is null or r.priority = :priority)
            """)
    Page<MaintenanceRequest> search(Long buildingId, Long staffId, String status, String priority,
            Pageable pageable);

    @Query("""
            select r from MaintenanceRequest r
            where r.buildingId in :buildingIds
              and (:staffId is null or r.staffId = :staffId)
              and (:status is null or r.status = :status)
              and (:priority is null or r.priority = :priority)
            """)
    Page<MaintenanceRequest> searchIn(Collection<Long> buildingIds, Long staffId, String status, String priority,
            Pageable pageable);
}
