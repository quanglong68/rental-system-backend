package com.rental.contracttenant.dto;

import java.math.BigDecimal;
import lombok.Value;

@Value
public class InternalContractDepositResponse {
    Long contractId;
    Long customerId;
    Long buildingId;
    Long roomId;
    String roomNumber;
    String status;
    String signatureStatus;
    BigDecimal depositAmount;
    boolean depositPaid;
    String depositTxnRef;
}

