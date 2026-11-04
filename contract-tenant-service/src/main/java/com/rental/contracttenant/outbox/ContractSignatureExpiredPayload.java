package com.rental.contracttenant.outbox;

/** Emitted when the signing deadline passes; the room is released and the request is rejected. */
public record ContractSignatureExpiredPayload(
        Long contractId,
        Long rentalRequestId,
        Long roomId,
        Long buildingId,
        Long customerId
) {
}
