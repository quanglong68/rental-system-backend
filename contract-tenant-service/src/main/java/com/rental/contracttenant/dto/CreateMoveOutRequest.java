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

    @NotNull(message = "contractId là bắt buộc")
    private Long contractId;

    @NotNull(message = "expectedMoveOutDate là bắt buộc")
    private LocalDate expectedMoveOutDate;

    /** Spec 9.4: {@code {contractId, expectedMoveOutDate, reason?}} — optional. */
    private String reason;
}
