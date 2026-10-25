package com.rental.billingutilityservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request gan dich vu vao hop dong (docs muc 9.5). PERSONAL bat buoc co contractTenantId. */
@Data
public class AttachServiceRequest {

    @NotNull
    private Long contractId;

    @NotNull
    private Long serviceId;

    @Min(1)
    private Integer quantity = 1;

    @NotNull
    private String usageType;

    private Long contractTenantId;
}
