package com.rental.contracttenant.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload of the manager's decision on a renewal request (approved or rejected). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractRenewalDecidedPayload {
    private Long contractId;
    private Long renewalRequestId;
    private Long roomId;
    private Long buildingId;
    private Long customerId;
    private Integer termMonths;
    private String roomNumber;
    private String buildingName;
    private String reason;
}
