package com.rental.contracttenant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class BillingContractData {

    Long contractId;
    Long roomId;
    Long buildingId;
    String roomNumber;
    Long customerId;
    String status;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal rentAmount;
    Integer paymentDueDay;
    List<BillingTenantData> tenants;

    public BillingContractData(Long contractId, Long roomId, Long buildingId, Long customerId,
                               String status, LocalDate startDate, LocalDate endDate,
                               BigDecimal rentAmount, List<BillingTenantData> tenants) {
        this(contractId, roomId, buildingId, null, customerId, status, startDate, endDate, rentAmount, null, tenants);
    }

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
