package com.rental.user.repository;

import com.rental.user.entity.StaffBuilding;
import com.rental.user.entity.StaffBuildingId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffBuildingRepository extends JpaRepository<StaffBuilding, StaffBuildingId> {
    List<StaffBuilding> findByIdStaffId(Long staffId);
    List<StaffBuilding> findByIdBuildingId(Long buildingId);
}
