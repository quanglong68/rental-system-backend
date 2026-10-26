package com.rental.asset.client;

import com.rental.common.feign.FeignDefaultConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-notification-service", configuration = FeignDefaultConfig.class)
public interface UserClient {

    @GetMapping("/internal/staff/{staffId}/buildings")
    StaffBuildingsResponse getStaffBuildings(@PathVariable Long staffId);

    record StaffBuildingsResponse(Long staffId, List<Long> buildingIds) {}
}
