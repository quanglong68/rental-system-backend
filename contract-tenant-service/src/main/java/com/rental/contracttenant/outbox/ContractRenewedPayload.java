package com.rental.contracttenant.outbox;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractRenewedPayload {
    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private LocalDate newEndDate;
    private List<Long> tenantIds;
}
