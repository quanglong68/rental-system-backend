package com.rental.user.repository;

import com.rental.user.entity.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    java.util.List<Staff> findByAdminTrue();
    @Query("select s from Staff s where (:buildingId is null or s.id in "
            + "(select b.id.staffId from StaffBuilding b where b.id.buildingId = :buildingId))")
    Page<Staff> findStaff(@Param("buildingId") Long buildingId, Pageable pageable);
}
