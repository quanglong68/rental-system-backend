package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.ContractDocument;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {

    Optional<ContractDocument> findFirstByContractIdOrderByVersionDesc(Long contractId);

    Optional<ContractDocument> findFirstByContractIdAndDocumentTypeOrderByVersionDesc(Long contractId, String documentType);

    @Query("select coalesce(max(d.version), 0) from ContractDocument d where d.contractId = :contractId")
    int findMaxVersion(@Param("contractId") Long contractId);
}
