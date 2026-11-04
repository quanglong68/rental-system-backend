package com.rental.contracttenant.dto;

import com.rental.contracttenant.entity.ContractSignature;
import com.rental.contracttenant.entity.SignatureParty;
import java.time.Instant;
import lombok.Value;

/** Signature audit exposed to the contract detail page. */
@Value
public class ContractSignatureResponse {

    Long id;
    SignatureParty party;
    Long signerId;
    String signerName;
    Instant signedAt;
    String pdfSha256;
    String consentVersion;

    public static ContractSignatureResponse from(ContractSignature signature) {
        return new ContractSignatureResponse(signature.getId(), signature.getParty(), signature.getSignerId(),
                signature.getSignerName(), signature.getSignedAt(), signature.getPdfSha256(),
                signature.getConsentVersion());
    }
}
