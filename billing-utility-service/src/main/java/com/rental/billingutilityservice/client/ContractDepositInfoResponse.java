package com.rental.billingutilityservice.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractDepositInfoResponse {
    private Long contractId;
    private Long customerId;
    private Long buildingId;
    private Long roomId;
    private String roomNumber;
    private String status;
    private String signatureStatus;
    private BigDecimal depositAmount;
    private boolean depositPaid;
    private String depositTxnRef;
}

