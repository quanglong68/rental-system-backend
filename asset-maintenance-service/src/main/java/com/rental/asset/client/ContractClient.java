package com.rental.asset.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "contract-tenant-service", configuration = FeignDefaultConfig.class)
public interface ContractClient {

    @GetMapping("/internal/rooms/{roomId}/tenants/{customerId}")
    TenantInfo getTenantInfo(@PathVariable Long roomId, @PathVariable Long customerId);

    record TenantInfo(boolean isTenant, Long contractId, Long buildingId) {}
}
