package com.rental.contracttenant.dto;

import com.rental.contracttenant.entity.ContractDocument;
import java.time.Instant;
import lombok.Value;

@Value
public class ContractDocumentResponse {

    Long id;
    int version;
    String objectKey;
    String sha256;
    Instant generatedAt;

    public static ContractDocumentResponse from(ContractDocument document) {
        return new ContractDocumentResponse(document.getId(), document.getVersion(), document.getObjectKey(),
                document.getSha256(), document.getGeneratedAt());
    }
}
