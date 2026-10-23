package com.rental.contracttenant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class RoomTenantCheckResponse {
    @JsonProperty("isTenant")
    boolean isTenant;
    Long contractId;
    Long buildingId;
}
