package com.rental.contracttenant.outbox;

import com.rental.contracttenant.entity.Contract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload of {@code ContractSigned} (docs muc 10): consumed by Property (room OCCUPIED), Listing
 * (close listing, reject pending requests, close roommate posts), Billing (contract_info) and
 * Notification. Field names are part of the cross-service contract — consumer code in
 * user-notification-service already reads {@code contractId} and {@code tenantIds}.
 *
 * <p>{@code tenantIds} are customer IDs, per the spec note on {@code EventEnvelope} payloads.
 * {@code endDate} is non-null here because signing rejects an incomplete contract.
 */
@Getter
@Setter
@NoArgsConstructor
public class ContractSignedPayload {

    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal rentAmount;
    private List<Long> tenantIds;

    static ContractSignedPayload of(Contract contract, List<Long> tenantIds) {
        ContractSignedPayload payload = new ContractSignedPayload();
        payload.setContractId(contract.getId());
        payload.setRoomId(contract.getRoomId());
        payload.setBuildingId(contract.getBuildingId());
        payload.setStartDate(contract.getStartDate());
        payload.setEndDate(contract.getEndDate());
        payload.setRentAmount(contract.getRentAmount());
        payload.setTenantIds(tenantIds);
        return payload;
    }
}
