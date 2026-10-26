package com.rental.asset;
import java.time.*; import java.util.*;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;

interface ApplianceRepository extends JpaRepository<Appliance,Long> {
 List<Appliance> findByRoomId(Long roomId); List<Appliance> findByBuildingId(Long buildingId); List<Appliance> findByBuildingIdAndStatus(Long buildingId,String status);
 Page<Appliance> findByRoomId(Long roomId,Pageable page); Page<Appliance> findByBuildingId(Long buildingId,Pageable page); Page<Appliance> findByBuildingIdAndStatus(Long buildingId,String status,Pageable page);
}
interface ReportRepository extends JpaRepository<MaintenanceReport,Long> {
 List<MaintenanceReport> findByCustomerIdOrderByCreatedAtDesc(Long customerId); List<MaintenanceReport> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId,String status);
 List<MaintenanceReport> findByBuildingIdAndStatusOrderByCreatedAtDesc(Long buildingId,String status); List<MaintenanceReport> findByBuildingIdOrderByCreatedAtDesc(Long buildingId);
 Page<MaintenanceReport> findByCustomerIdOrderByCreatedAtDesc(Long customerId,Pageable page); Page<MaintenanceReport> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId,String status,Pageable page);
 Page<MaintenanceReport> findByBuildingIdAndStatusOrderByCreatedAtDesc(Long buildingId,String status,Pageable page); Page<MaintenanceReport> findByBuildingIdOrderByCreatedAtDesc(Long buildingId,Pageable page);
}
interface RequestRepository extends JpaRepository<MaintenanceRequest,Long> {
 List<MaintenanceRequest> findByStaffIdOrderByCreatedAtDesc(Long staffId); List<MaintenanceRequest> findByBuildingIdOrderByCreatedAtDesc(Long buildingId); List<MaintenanceRequest> findByReportId(Long reportId);
 List<MaintenanceRequest> findByReportIdIn(List<Long> reportIds);
 Page<MaintenanceRequest> findByStaffIdOrderByCreatedAtDesc(Long staffId,Pageable page); Page<MaintenanceRequest> findByBuildingIdOrderByCreatedAtDesc(Long buildingId,Pageable page);
 @Query("select m from MaintenanceRequest m where "
         + "(:buildingId is null or m.buildingId = :buildingId) and "
         + "(:staffId is null or m.staffId = :staffId) and "
         + "(:status is null or m.status = :status) and "
         + "(:priority is null or m.priority = :priority) "
         + "order by m.createdAt desc")
 Page<MaintenanceRequest> findRequests(
         @Param("buildingId") Long buildingId,
         @Param("staffId") Long staffId,
         @Param("status") String status,
         @Param("priority") String priority,
         Pageable page);
 @Query("select m from MaintenanceRequest m where m.reportId in "
         + "(select r.id from MaintenanceReport r where r.customerId = :customerId) and "
         + "(:status is null or m.status = :status) and "
         + "(:priority is null or m.priority = :priority) "
         + "order by m.createdAt desc")
 Page<MaintenanceRequest> findByCustomerReports(
         @Param("customerId") Long customerId,
         @Param("status") String status,
         @Param("priority") String priority,
         Pageable page);
}
interface RecordRepository extends JpaRepository<MaintenanceRecord,Long> { Optional<MaintenanceRecord> findByMaintenanceRequestId(Long id); }
interface ScheduleRepository extends JpaRepository<MaintenanceSchedule,Long> {
 List<MaintenanceSchedule> findByActiveTrue();
 List<MaintenanceSchedule> findByBuildingIdAndActiveTrue(Long buildingId);
 List<MaintenanceSchedule> findByStaffIdAndActiveTrue(Long staffId);
 List<MaintenanceSchedule> findByBuildingIdAndStaffIdAndActiveTrue(Long buildingId,Long staffId);
 List<MaintenanceSchedule> findByActiveTrueAndNextDateBetween(LocalDate from,LocalDate to);
}
interface OutboxRepository extends JpaRepository<OutboxEvent,UUID> {
 @Query(value="select * from outbox_event where published_at is null order by occurred_at limit 100 for update skip locked",nativeQuery=true) List<OutboxEvent> lockPending();
}
