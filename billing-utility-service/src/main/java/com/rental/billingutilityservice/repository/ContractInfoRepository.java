package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.ContractInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractInfoRepository extends JpaRepository<ContractInfo, Long> {
    List<ContractInfo> findByBuildingId(Long buildingId);
}
