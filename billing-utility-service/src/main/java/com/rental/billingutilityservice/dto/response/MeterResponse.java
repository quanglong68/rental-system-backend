package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Response dong ho (docs muc 9.5). */
@Value
@Builder
public class MeterResponse {
    Long id;
    Long roomId;
    Long buildingId;
    Long utilityTypeId;
    String utilityTypeName;
    String meterCode;
    BigDecimal initialIndex;
    boolean active;
}
