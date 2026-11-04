package com.rental.listingbooking.repository;

import com.rental.listingbooking.entity.RentalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RentalRequestRepository extends JpaRepository<RentalRequest, Long> {

    Optional<RentalRequest> findByCustomerIdAndListingIdAndStatus(Long customerId, Long listingId, String status);

    List<RentalRequest> findByCustomerIdAndListingIdAndStatusIn(
            Long customerId, Long listingId, Collection<String> statuses);

    Page<RentalRequest> findByCustomerId(Long customerId, Pageable pageable);

    Page<RentalRequest> findByCustomerIdAndStatus(Long customerId, String status, Pageable pageable);

    @Query("SELECT r FROM RentalRequest r WHERE " +
           "(:buildingId IS NULL OR r.buildingId = :buildingId) " +
           "AND (:listingId IS NULL OR r.listingId = :listingId) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:unread = FALSE OR r.readAt IS NULL)")
    Page<RentalRequest> findManage(
            @Param("buildingId") Long buildingId,
            @Param("listingId") Long listingId,
            @Param("status") String status,
            @Param("unread") boolean unread,
            Pageable pageable);

    @Query("SELECT r FROM RentalRequest r WHERE r.buildingId IN :buildingIds " +
           "AND (:listingId IS NULL OR r.listingId = :listingId) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:unread = FALSE OR r.readAt IS NULL)")
    Page<RentalRequest> findManageIn(
            @Param("buildingIds") Collection<Long> buildingIds,
            @Param("listingId") Long listingId,
            @Param("status") String status,
            @Param("unread") boolean unread,
            Pageable pageable);

    List<RentalRequest> findByListingIdAndStatus(Long listingId, String status);

    List<RentalRequest> findByRoomIdAndStatus(Long roomId, String status);

    List<RentalRequest> findByRoomIdAndStatusIn(Long roomId, Collection<String> statuses);
}
