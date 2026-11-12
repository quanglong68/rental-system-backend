package com.rental.contracttenant.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Signing request. The party is inferred from the caller's role: CUSTOMER signs as TENANT,
 * QUAN_LY/ADMIN signs as LESSOR. The password is re-checked against auth-service.
 */
public record SignContractRequest(
        @NotBlank String password,
        @NotBlank String signatureImageKey,
        String consentVersion,
        boolean acceptedTerms,
        Boolean depositConfirmed
) {
    @JsonCreator
    public SignContractRequest(
            @JsonProperty("password") String password,
            @JsonProperty("signatureImageKey") String signatureImageKey,
            @JsonProperty("consentVersion") String consentVersion,
            @JsonProperty("acceptedTerms") boolean acceptedTerms,
            @JsonProperty("depositConfirmed") Boolean depositConfirmed) {
        this.password = password;
        this.signatureImageKey = signatureImageKey;
        this.consentVersion = consentVersion;
        this.acceptedTerms = acceptedTerms;
        this.depositConfirmed = depositConfirmed;
    }

    public SignContractRequest(String password, String signatureImageKey, String consentVersion, boolean acceptedTerms) {
        this(password, signatureImageKey, consentVersion, acceptedTerms, null);
    }
}
