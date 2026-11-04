package com.rental.contracttenant.outbox;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractRenewalRequestedPayload {
    private Long contractId;
    private Long oldContractId;
    private Long roomId;
    private Long buildingId;
    private Long customerId;
    private String roomNumber;
    private String buildingName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer termMonths;
}
