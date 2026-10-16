package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.Contract;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByRentalRequestId(Long rentalRequestId);
}
