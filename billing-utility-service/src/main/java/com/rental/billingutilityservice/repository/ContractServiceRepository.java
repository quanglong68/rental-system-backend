package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.ContractService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractServiceRepository extends JpaRepository<ContractService, Long> {
    List<ContractService> findByContractId(Long contractId);
}
