package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request dang ky dong ho (docs muc 9.5): {roomId, utilityTypeId, meterCode?, initialIndex}. */
@Data
public class CreateMeterRequest {

    @NotNull
    private Long roomId;

    @NotNull
    private Long utilityTypeId;

    private String meterCode;

    @DecimalMin("0.0")
    private java.math.BigDecimal initialIndex;
}
