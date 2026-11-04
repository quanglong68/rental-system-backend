package com.rental.contracttenant.outbox;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload of {@code TenantAdded}. {@code contractTenantId} is the assignment identity the Property
 * projection stores; {@code customerId} is who moved in and {@code joinedAt} is the date they did.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantAddedPayload {
    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private Long contractTenantId;
    private Long customerId;
    private LocalDate joinedAt;
    private Long applicationId;
    private Long ownerCustomerId;
    private Long sourceRevision;
}
