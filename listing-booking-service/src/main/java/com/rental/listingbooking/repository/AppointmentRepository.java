package com.rental.listingbooking.repository;

import com.rental.listingbooking.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Page<Appointment> findByCustomerId(Long customerId, Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE " +
           "(:staffId IS NULL OR a.staffId = :staffId) " +
           "AND (:buildingId IS NULL OR a.buildingId = :buildingId) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:unread = FALSE OR a.readAt IS NULL) " +
           "AND a.appointmentDate >= :fromTime " +
           "AND a.appointmentDate <= :toTime")
    Page<Appointment> search(
            @Param("staffId") Long staffId,
            @Param("buildingId") Long buildingId,
            @Param("status") String status,
            @Param("unread") boolean unread,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.buildingId IN :buildingIds " +
           "AND (:staffId IS NULL OR a.staffId = :staffId) " +
           "AND (:buildingId IS NULL OR a.buildingId = :buildingId) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:unread = FALSE OR a.readAt IS NULL) " +
           "AND a.appointmentDate >= :fromTime " +
           "AND a.appointmentDate <= :toTime")
    Page<Appointment> searchIn(
            @Param("buildingIds") Collection<Long> buildingIds,
            @Param("staffId") Long staffId,
            @Param("buildingId") Long buildingId,
            @Param("status") String status,
            @Param("unread") boolean unread,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);
}
