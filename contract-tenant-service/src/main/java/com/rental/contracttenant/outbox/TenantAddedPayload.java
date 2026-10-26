package com.rental.contracttenant.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantAddedPayload {
    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private Long customerId;
    private Long applicationId;
    private Long ownerCustomerId;
}
