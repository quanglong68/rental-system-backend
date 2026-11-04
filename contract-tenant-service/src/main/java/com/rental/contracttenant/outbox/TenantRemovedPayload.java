package com.rental.contracttenant.outbox;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload of {@code TenantRemoved}: the assignment row that ended and the date it ended. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantRemovedPayload {
    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private Long contractTenantId;
    private Long customerId;
    private LocalDate leftAt;
    private Long sourceRevision;
}
