package com.rental.contracttenant.dto;

public record ChatContractContext(
        Long contractId,
        Long customerId,
        Long buildingId,
        Long roomId,
        String roomNumber,
        String buildingName,
        String status) {
}
