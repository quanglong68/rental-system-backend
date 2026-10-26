package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Response chi so (docs muc 9.5). */
@Value
@Builder
public class MeterReadingResponse {
    Long id;
    Long meterId;
    Long roomId;
    Integer month;
    Integer year;
    LocalDate readingDate;
    BigDecimal prevIndex;
    BigDecimal currentIndex;
    BigDecimal consumption;
    BigDecimal unitPrice;
    BigDecimal amount;
}
