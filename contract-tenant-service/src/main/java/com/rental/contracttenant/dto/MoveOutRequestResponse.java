package com.rental.contracttenant.dto;

import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Value;

@Value
public class MoveOutRequestResponse {

    Long id;
    Long contractId;
    Long requestedBy;
    LocalDate requestDate;
    LocalDate expectedMoveOutDate;
    String reason;
    MoveOutStatus status;
    Instant createdAt;

    public static MoveOutRequestResponse from(MoveOutRequest req) {
        return new MoveOutRequestResponse(
                req.getId(),
                req.getContractId(),
                req.getRequestedBy(),
                req.getRequestDate(),
                req.getExpectedMoveOutDate(),
                req.getReason(),
                req.getStatus(),
                req.getCreatedAt()
        );
    }
}
