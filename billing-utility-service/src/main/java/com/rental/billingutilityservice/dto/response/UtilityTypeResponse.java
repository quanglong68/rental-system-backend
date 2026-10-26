package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

/** Response loai tien ich: {id, name, unit, unitPrice} (docs muc 9.5). */
@Value
@Builder
public class UtilityTypeResponse {
    Long id;
    String name;
    String unit;
    BigDecimal unitPrice;
    Instant createdAt;
}
