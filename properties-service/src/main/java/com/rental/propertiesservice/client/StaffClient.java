package com.rental.propertiesservice.client;

import com.rental.common.feign.FeignDefaultConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign call #5 of the closed list (spec 5.2): Property → User,
 * {@code GET /internal/staff/{staffId}/buildings}, used to enforce the building scope of non-ADMIN
 * staff (spec 6.2). Same contract as contract-tenant-service's {@code StaffClient}.
 */
@FeignClient(name = "user-notification-service", configuration = FeignDefaultConfig.class)
public interface StaffClient {

    @GetMapping("/internal/staff/{staffId}/buildings")
    StaffBuildings staffBuildings(@PathVariable("staffId") Long staffId);

    record StaffBuildings(Long staffId, List<Long> buildingIds) {
    }
}
