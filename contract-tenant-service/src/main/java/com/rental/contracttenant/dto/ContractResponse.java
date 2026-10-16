package com.rental.contracttenant.dto;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Value;

@Value
public class ContractResponse {

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

    public static ContractResponse from(Contract contract) {
        return new ContractResponse(contract.getId(), contract.getRentalRequestId(), contract.getRoomId(),
                contract.getBuildingId(), contract.getCustomerId(), contract.getStatus(), contract.getStartDate(),
                contract.getEndDate(), contract.getRentAmount(), contract.getDepositAmount(),
                contract.getTerminationReason(), contract.getCreatedAt());
    }
}
