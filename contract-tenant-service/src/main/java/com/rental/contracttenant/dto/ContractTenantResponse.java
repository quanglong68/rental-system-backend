package com.rental.contracttenant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rental.contracttenant.entity.ContractTenant;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Value;

/** 201 body of {@code POST /api/v1/contracts/{id}/tenants} (spec 9.4: {@code 201 ContractTenant}). */
@Value
public class ContractTenantResponse {

    Long id;
    Long contractId;
    Long customerId;
    LocalDate joinedAt;
    LocalDate leftAt;
    @JsonProperty("isRepresentative")
    boolean isRepresentative;
    Instant createdAt;

    public static ContractTenantResponse from(ContractTenant tenant) {
        return new ContractTenantResponse(tenant.getId(), tenant.getContractId(), tenant.getCustomerId(),
                tenant.getJoinedAt(), tenant.getLeftAt(), tenant.isRepresentative(), tenant.getCreatedAt());
    }
}
