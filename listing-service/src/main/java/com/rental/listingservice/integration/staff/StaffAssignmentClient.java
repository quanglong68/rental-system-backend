package com.rental.listingservice.integration.staff;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-notification-service", configuration = {
        FeignDefaultConfig.class, StaffAssignmentFeignConfig.class
})
public interface StaffAssignmentClient {

    @GetMapping("/internal/staff/{staffId}/buildings")
    StaffBuildingsResponse getStaffBuildings(@PathVariable("staffId") Long staffId);
}
