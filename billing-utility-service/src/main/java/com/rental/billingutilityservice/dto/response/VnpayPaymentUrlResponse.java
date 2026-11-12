package com.rental.billingutilityservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnpayPaymentUrlResponse {
    private String paymentUrl;
    private String txnRef;
    private Long invoiceId;
    private Long contractId;
    private BigDecimal amount;
}
