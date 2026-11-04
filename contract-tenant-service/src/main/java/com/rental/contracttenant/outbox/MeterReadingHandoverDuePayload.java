package com.rental.contracttenant.outbox;

import com.rental.contracttenant.entity.Contract;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload of {@code MeterReadingHandoverDue}: emitted on the move-in date of a newly activated
 * contract so technicians are asked to record the handover electricity/water indexes.
 *
 * <p>One event per contract: the outbox publisher drops duplicates by contract id, which also covers
 * the daily catch-up scan for contracts that became ACTIVE before their start date.
 */
@Getter
@Setter
@NoArgsConstructor
public class MeterReadingHandoverDuePayload {

    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private String roomNumber;
    private String buildingName;
    private LocalDate moveInDate;

    public static MeterReadingHandoverDuePayload of(Contract contract) {
        MeterReadingHandoverDuePayload payload = new MeterReadingHandoverDuePayload();
        payload.setContractId(contract.getId());
        payload.setRoomId(contract.getRoomId());
        payload.setBuildingId(contract.getBuildingId());
        payload.setRoomNumber(contract.getRoomNumber());
        payload.setBuildingName(contract.getBuildingName());
        payload.setMoveInDate(contract.getStartDate());
        return payload;
    }
}
