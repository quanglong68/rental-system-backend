package com.rental.contracttenant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Signing request. The party is inferred from the caller's role: CUSTOMER signs as TENANT,
 * QUAN_LY/ADMIN signs as LESSOR. The password is re-checked against auth-service.
 */
public record SignContractRequest(
        @NotBlank String password,
        @NotBlank String signatureImageKey,
        String consentVersion,
        boolean acceptedTerms
) {
}
