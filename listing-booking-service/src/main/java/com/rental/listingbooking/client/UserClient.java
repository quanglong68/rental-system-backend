package com.rental.listingbooking.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-notification-service", configuration = FeignDefaultConfig.class)
public interface UserClient {

    record StaffBuildingsResponse(Long staffId, List<Long> buildingIds) {}

    record CustomerSummary(Long id, String fullName) {}

    @GetMapping("/internal/staff/{staffId}/buildings")
    StaffBuildingsResponse getStaffBuildings(@PathVariable("staffId") Long staffId);

    @GetMapping("/internal/customers")
    List<CustomerSummary> getCustomerSummaries(@RequestParam("ids") List<Long> ids);
}
