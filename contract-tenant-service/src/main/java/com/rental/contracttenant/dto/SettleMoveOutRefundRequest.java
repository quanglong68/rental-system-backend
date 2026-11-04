package com.rental.contracttenant.dto;

import java.math.BigDecimal;

public record SettleMoveOutRefundRequest(
        BigDecimal refundAmount,
        BigDecimal deductionAmount,
        String deductionReason,
        String refundStatus
) {}
