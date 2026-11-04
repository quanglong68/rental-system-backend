package com.rental.contracttenant.outbox;

/** Emitted when the tenant refuses to sign; listing-booking returns the rental request to VERIFIED. */
public record ContractSignatureDeclinedPayload(
        Long contractId,
        Long rentalRequestId,
        Long roomId,
        Long buildingId,
        Long customerId,
        String reason
) {
}
