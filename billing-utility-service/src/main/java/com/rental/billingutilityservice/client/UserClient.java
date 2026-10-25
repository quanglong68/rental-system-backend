package com.rental.billingutilityservice.client;

import com.rental.common.feign.FeignDefaultConfig;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/** Feign #5 (docs muc 5.2, 6.2): kiem tra staff co phu trach toa nha (cache 5 phut). */
@FeignClient(name = "user-notification-service", configuration = FeignDefaultConfig.class)
public interface UserClient {

    @GetMapping("/internal/staff/{staffId}/buildings")
    StaffBuildings getStaffBuildings(@PathVariable("staffId") Long staffId);

    @Data
    class StaffBuildings {
        private Long staffId;
        private List<Long> buildingIds;
    }
}
