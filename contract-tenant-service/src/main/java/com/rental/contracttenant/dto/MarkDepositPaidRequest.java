package com.rental.contracttenant.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarkDepositPaidRequest {

    @NotBlank(message = "txnRef không được để trống")
    private String txnRef;

    private BigDecimal amount;

    private String method;
}

