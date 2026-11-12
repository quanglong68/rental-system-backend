package com.rental.contracttenant.outbox;

import com.rental.contracttenant.entity.Contract;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload of {@code ContractDepositPaid}: emitted once when the tenant's contract deposit is
 * recorded as paid (e.g. via VNPay) so building managers receive an immediate bell alert.
 */
@Getter
@Setter
@NoArgsConstructor
public class ContractDepositPaidPayload {

    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private Long customerId;
    private String roomNumber;
    private String buildingName;
    private String tenantFullName;
    private BigDecimal depositAmount;
    private String depositPaymentMethod;
    private String depositTxnRef;
    private Instant depositPaidAt;

    public static ContractDepositPaidPayload of(Contract contract) {
        ContractDepositPaidPayload payload = new ContractDepositPaidPayload();
        payload.setContractId(contract.getId());
        payload.setRoomId(contract.getRoomId());
        payload.setBuildingId(contract.getBuildingId());
        payload.setCustomerId(contract.getCustomerId());
        payload.setRoomNumber(contract.getRoomNumber());
        payload.setBuildingName(contract.getBuildingName());
        payload.setTenantFullName(contract.getTenantFullName());
        payload.setDepositAmount(contract.getDepositAmount());
        payload.setDepositPaymentMethod(contract.getDepositPaymentMethod());
        payload.setDepositTxnRef(contract.getDepositTxnRef());
        payload.setDepositPaidAt(contract.getDepositPaidAt());
        return payload;
    }
}

