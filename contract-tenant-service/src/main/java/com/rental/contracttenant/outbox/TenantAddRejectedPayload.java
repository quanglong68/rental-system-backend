package com.rental.contracttenant.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantAddRejectedPayload {
    private Long roomId;
    private Long buildingId;
    private Long applicationId;
    private Long ownerCustomerId;
    private Long applicantCustomerId;
    private String reason;
}
