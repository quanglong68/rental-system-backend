package com.rental.contracttenant.dto;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Value;

@Value
public class ContractDetailResponse {

    Long id;
    Long rentalRequestId;
    Long roomId;
    Long buildingId;
    Long customerId;
    ContractStatus status;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal rentAmount;
    BigDecimal depositAmount;
    String terminationReason;
    Instant createdAt;
    List<ContractTenantResponse> tenants;

    public static ContractDetailResponse from(Contract contract, List<ContractTenant> tenants) {
        List<ContractTenantResponse> tenantResponses = tenants == null ? List.of() :
                tenants.stream().map(ContractTenantResponse::from).toList();
        return new ContractDetailResponse(
                contract.getId(),
                contract.getRentalRequestId(),
                contract.getRoomId(),
                contract.getBuildingId(),
                contract.getCustomerId(),
                contract.getStatus(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getRentAmount(),
                contract.getDepositAmount(),
                contract.getTerminationReason(),
                contract.getCreatedAt(),
                tenantResponses
        );
    }
}
