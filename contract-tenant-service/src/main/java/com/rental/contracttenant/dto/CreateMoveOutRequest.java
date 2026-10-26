package com.rental.contracttenant.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMoveOutRequest {

    @NotNull(message = "contractId is required")
    private Long contractId;

    @NotNull(message = "expectedMoveOutDate is required")
    private LocalDate expectedMoveOutDate;

    /** Spec 9.4: {@code {contractId, expectedMoveOutDate, reason?}} — optional. */
    private String reason;
}
