package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

/** Response thanh toan (docs muc 9.5). */
@Value
@Builder
public class PaymentResponse {
    Long id;
    Long invoiceId;
    BigDecimal amountPaid;
    String method;
    Instant paidAt;
    String note;
    Long recordedBy;
}
