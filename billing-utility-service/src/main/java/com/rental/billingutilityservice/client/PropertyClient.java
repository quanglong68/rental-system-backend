package com.rental.billingutilityservice.client;

import com.rental.common.feign.FeignDefaultConfig;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Feign #6 (docs muc 5.2): Billing goi Property lay buildingId khi dang ky dong ho. */
@FeignClient(name = "property-service", configuration = FeignDefaultConfig.class)
public interface PropertyClient {

    @GetMapping("/internal/rooms/{roomId}")
    RoomInternal getRoom(@PathVariable("roomId") Long roomId);

    @Data
    class RoomInternal {
        private Long id;
        private Long buildingId;
        private String roomNumber;
        private Integer maxCapacity;
        private String status;
    }
}
