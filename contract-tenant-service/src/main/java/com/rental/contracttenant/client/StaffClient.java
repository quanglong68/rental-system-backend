package com.rental.contracttenant.client;

import com.rental.common.feign.FeignDefaultConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign call #5 of the closed list (spec 5.2): Contract → User, {@code GET /internal/staff/{staffId}/buildings},
 * used to check that a non-ADMIN staff member is assigned to the building they are acting on
 * (spec 6.2 "Phạm vi tòa nhà"; spec 1027 lists this call among the two the contract service uses).
 *
 * <p>The response shape is {@code {staffId, buildingIds}} as actually implemented by
 * user-notification-service's {@code UserProfileController.internalStaffBuildings}, and as already
 * mirrored by asset-maintenance-service's {@code Dependencies.UserClient}. Same payload, same caller
 * conventions — the two services must not drift on it.
 */
@FeignClient(name = "user-notification-service", configuration = FeignDefaultConfig.class)
public interface StaffClient {

    @GetMapping("/internal/staff/{staffId}/buildings")
    StaffBuildings staffBuildings(@PathVariable("staffId") Long staffId);

    record StaffBuildings(Long staffId, List<Long> buildingIds) {
    }
}
