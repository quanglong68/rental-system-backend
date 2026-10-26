package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

/** Response dich vu gan hop dong (docs muc 9.5). */
@Value
@Builder
public class ContractServiceResponse {
    Long id;
    Long contractId;
    Long serviceId;
    String serviceName;
    Integer quantity;
    String usageType;
    Long contractTenantId;
}
