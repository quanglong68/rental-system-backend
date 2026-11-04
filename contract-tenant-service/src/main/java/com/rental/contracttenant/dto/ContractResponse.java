package com.rental.contracttenant.dto;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.SignatureStatus;
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
    SignatureStatus signatureStatus;
    Instant signatureDeadline;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal rentAmount;
    BigDecimal depositAmount;
    String terminationReason;
    Instant createdAt;
    Long renewedFromContractId;
    Long renewalRequestId;

    public static ContractResponse from(Contract contract) {
        return new ContractResponse(contract.getId(), contract.getRentalRequestId(), contract.getRoomId(),
                contract.getBuildingId(), contract.getCustomerId(), contract.getStatus(),
                contract.getSignatureStatus(), contract.getSignatureDeadline(), contract.getStartDate(),
                contract.getEndDate(), contract.getRentAmount(), contract.getDepositAmount(),
                contract.getTerminationReason(), contract.getCreatedAt(), contract.getRenewedFromContractId(),
                contract.getRenewalRequestId());
    }
}
