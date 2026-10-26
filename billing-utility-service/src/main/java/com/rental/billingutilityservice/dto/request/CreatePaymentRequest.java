package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** Request ghi nhan thanh toan tien mat (docs muc 9.5): method mac dinh CASH. */
@Data
public class CreatePaymentRequest {

    @NotNull
    private Long invoiceId;

    @NotNull
    @DecimalMin("1")
    private BigDecimal amountPaid;

    private Instant paidAt;

    private String note;
}
