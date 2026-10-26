package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.UtilityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilityTypeRepository extends JpaRepository<UtilityType, Long> {
    Optional<UtilityType> findByNameIgnoreCase(String name);
}
