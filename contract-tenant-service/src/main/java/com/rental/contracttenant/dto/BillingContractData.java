package com.rental.contracttenant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Value;

@Value
public class BillingContractData {

    Long contractId;
    Long roomId;
    Long buildingId;
    Long customerId;
    String status;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal rentAmount;
    List<BillingTenantData> tenants;

    @Value
    public static class BillingTenantData {
        Long contractTenantId;
        Long customerId;
        LocalDate joinedAt;
        LocalDate leftAt;
        @JsonProperty("isRepresentative")
        boolean isRepresentative;
    }
}
