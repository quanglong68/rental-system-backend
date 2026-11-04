package com.rental.listingbooking.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "contract-tenant-service", configuration = FeignDefaultConfig.class)
public interface ContractClient {

    record RoomTenantCheckResponse(boolean isTenant, Long contractId, Long buildingId) {}

    @GetMapping("/internal/rooms/{roomId}/tenants/{customerId}")
    RoomTenantCheckResponse checkRoomTenant(@PathVariable("roomId") Long roomId, @PathVariable("customerId") Long customerId);
}
