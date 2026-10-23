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
public class RenewContractRequest {

    @NotNull(message = "newEndDate is required")
    private LocalDate newEndDate;
}
