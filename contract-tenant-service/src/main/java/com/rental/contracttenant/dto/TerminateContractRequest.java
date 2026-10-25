package com.rental.contracttenant.dto;

import jakarta.validation.constraints.NotBlank;
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
public class TerminateContractRequest {

    @NotNull(message = "terminationDate is required")
    private LocalDate terminationDate;

    @NotBlank(message = "reason is required")
    private String reason;
}
