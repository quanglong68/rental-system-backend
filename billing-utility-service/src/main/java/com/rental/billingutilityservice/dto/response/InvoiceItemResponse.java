package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Response dong hoa don (docs muc 9.5). */
@Value
@Builder
public class InvoiceItemResponse {
    Long id;
    String itemType;
    String description;
    BigDecimal amount;
    Long meterReadingId;
    Long contractServiceId;
}
