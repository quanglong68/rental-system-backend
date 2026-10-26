package com.rental.asset.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "property-service", configuration = FeignDefaultConfig.class)
public interface PropertyClient {

    @GetMapping("/internal/rooms/{roomId}")
    RoomInfo getRoom(@PathVariable Long roomId);

    record RoomInfo(Long id, Long buildingId) {}
}
