package com.rental.listingbooking.repository;

import com.rental.listingbooking.entity.RoomListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface RoomListingRepository extends JpaRepository<RoomListing, Long> {

    Optional<RoomListing> findByRoomId(Long roomId);

    Optional<RoomListing> findByRoomIdAndStatus(Long roomId, String status);

    @Query("SELECT l FROM RoomListing l WHERE l.status = 'ACTIVE' " +
           "AND (:wardId IS NULL OR l.wardId = :wardId) " +
           "AND (:districtId IS NULL OR l.districtId = :districtId) " +
           "AND (:buildingId IS NULL OR l.buildingId = :buildingId) " +
           "AND (:roomTypeId IS NULL OR l.roomTypeId = :roomTypeId) " +
           "AND (:minPrice IS NULL OR l.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR l.price <= :maxPrice) " +
           "AND (cast(:keyword as String) IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', cast(:keyword as String), '%')) OR LOWER(l.buildingName) LIKE LOWER(CONCAT('%', cast(:keyword as String), '%')) OR LOWER(l.address) LIKE LOWER(CONCAT('%', cast(:keyword as String), '%')))")
    Page<RoomListing> searchActive(
            @Param("wardId") Long wardId,
            @Param("districtId") Long districtId,
            @Param("buildingId") Long buildingId,
            @Param("roomTypeId") Long roomTypeId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT l FROM RoomListing l WHERE " +
           "(:buildingId IS NULL OR l.buildingId = :buildingId) " +
           "AND (:status IS NULL OR l.status = :status)")
    Page<RoomListing> findManage(
            @Param("buildingId") Long buildingId,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT l FROM RoomListing l WHERE l.buildingId IN :buildingIds " +
           "AND (:status IS NULL OR l.status = :status)")
    Page<RoomListing> findManageIn(
            @Param("buildingIds") Collection<Long> buildingIds,
            @Param("status") String status,
            Pageable pageable);
}
