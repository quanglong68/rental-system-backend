package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateVnpayPaymentRequest {

    @NotNull(message = "invoiceId không được để trống")
    private Long invoiceId;

    private BigDecimal amount;

    private String returnUrl;
}
