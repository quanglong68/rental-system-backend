package com.rental.listingbooking.repository;

import com.rental.listingbooking.entity.RoommatePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface RoommatePostRepository extends JpaRepository<RoommatePost, Long> {

    List<RoommatePost> findByCustomerId(Long customerId);

    List<RoommatePost> findByRoomIdAndStatus(Long roomId, String status);

    @Query("SELECT p FROM RoommatePost p WHERE p.status = 'OPEN' " +
           "AND (:buildingId IS NULL OR p.buildingId = :buildingId) " +
           "AND (:maxBudget IS NULL OR p.budgetShare <= :maxBudget) " +
           "AND (cast(:keyword as String) IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', cast(:keyword as String), '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', cast(:keyword as String), '%')))")
    Page<RoommatePost> searchOpen(
            @Param("buildingId") Long buildingId,
            @Param("maxBudget") BigDecimal maxBudget,
            @Param("keyword") String keyword,
            Pageable pageable);
}
