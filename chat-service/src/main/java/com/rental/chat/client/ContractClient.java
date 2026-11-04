package com.rental.chat.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "contract-tenant-service", configuration = FeignDefaultConfig.class)
public interface ContractClient {

    record ActiveContract(Long contractId, Long customerId, Long buildingId, Long roomId, String roomNumber,
            String buildingName, String status) {
    }

    @GetMapping("/internal/contracts/customers/{customerId}/active")
    ActiveContract activeContract(@PathVariable("customerId") Long customerId);
}
