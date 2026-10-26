package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Response dich vu toa nha (docs muc 9.5). */
@Value
@Builder
public class BuildingServiceResponse {
    Long id;
    Long buildingId;
    String name;
    String unit;
    BigDecimal unitPrice;
}
