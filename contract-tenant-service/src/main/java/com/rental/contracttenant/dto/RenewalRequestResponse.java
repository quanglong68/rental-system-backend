package com.rental.contracttenant.dto;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractRenewalRequest;
import com.rental.contracttenant.entity.RenewalRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Value;

/**
 * View of a renewal request plus the proposed term and the linked draft (if the manager has prepared
 * it). The tenant UI uses {@code renewalContractId}/{@code renewalSignatureStatus} to move from
 * "waiting for the owner to send the contract" to the e-signature card without guessing from the
 * contract list.
 */
@Value
public class RenewalRequestResponse {

    Long id;
    Long contractId;
    Long roomId;
    Long buildingId;
    Long customerId;
    Long requestedBy;
    Integer termMonths;
    String note;
    RenewalRequestStatus status;
    Long decidedBy;
    Instant decidedAt;
    String rejectReason;
    LocalDate startDate;
    LocalDate endDate;
    Long renewalContractId;
    String renewalContractStatus;
    String renewalSignatureStatus;
    String roomNumber;
    String buildingName;
    Instant createdAt;
    Instant updatedAt;

    public static RenewalRequestResponse from(ContractRenewalRequest request, Contract oldContract,
            Contract renewalContract) {
        LocalDate start = oldContract.getEndDate() == null ? null : oldContract.getEndDate().plusDays(1);
        LocalDate end = start == null || request.getTermMonths() == null
                ? null
                : start.plusMonths(request.getTermMonths());
        return new RenewalRequestResponse(
                request.getId(),
                request.getContractId(),
                oldContract.getRoomId(),
                oldContract.getBuildingId(),
                oldContract.getCustomerId(),
                request.getRequestedBy(),
                request.getTermMonths(),
                request.getNote(),
                request.getStatus(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getRejectReason(),
                start,
                end,
                renewalContract == null ? null : renewalContract.getId(),
                renewalContract == null ? null : renewalContract.getStatus().name(),
                renewalContract == null ? null : renewalContract.getSignatureStatus().name(),
                oldContract.getRoomNumber(),
                oldContract.getBuildingName(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
