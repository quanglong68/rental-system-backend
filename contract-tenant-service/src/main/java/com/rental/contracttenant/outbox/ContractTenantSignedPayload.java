package com.rental.contracttenant.outbox;

import com.rental.contracttenant.entity.Contract;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload of {@code ContractTenantSigned}: emitted when the tenant signs the contract draft,
 * prompting building managers to verify deposit/terms and countersign to activate.
 */
@Getter
@Setter
@NoArgsConstructor
public class ContractTenantSignedPayload {

    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private Long customerId;
    private String roomNumber;
    private String buildingName;
    private String tenantFullName;
    private BigDecimal depositAmount;
    private boolean depositPaid;
    private String depositPaymentMethod;
    private String depositTxnRef;
    private Instant signedAt;

    public static ContractTenantSignedPayload of(Contract contract, Instant signedAt) {
        ContractTenantSignedPayload payload = new ContractTenantSignedPayload();
        payload.setContractId(contract.getId());
        payload.setRoomId(contract.getRoomId());
        payload.setBuildingId(contract.getBuildingId());
        payload.setCustomerId(contract.getCustomerId());
        payload.setRoomNumber(contract.getRoomNumber());
        payload.setBuildingName(contract.getBuildingName());
        payload.setTenantFullName(contract.getTenantFullName());
        payload.setDepositAmount(contract.getDepositAmount());
        payload.setDepositPaid(contract.isDepositPaid());
        payload.setDepositPaymentMethod(contract.getDepositPaymentMethod());
        payload.setDepositTxnRef(contract.getDepositTxnRef());
        payload.setSignedAt(signedAt);
        return payload;
    }
}

