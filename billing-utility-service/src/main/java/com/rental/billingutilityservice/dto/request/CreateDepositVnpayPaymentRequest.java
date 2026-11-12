package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDepositVnpayPaymentRequest {

    @NotNull(message = "contractId không được để trống")
    private Long contractId;

    private String returnUrl;
}

