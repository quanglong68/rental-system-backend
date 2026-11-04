package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.ContractSignature;
import com.rental.contracttenant.entity.SignatureParty;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Long> {

    Optional<ContractSignature> findByContractIdAndParty(Long contractId, SignatureParty party);

    List<ContractSignature> findByContractIdOrderBySignedAtAsc(Long contractId);
}
