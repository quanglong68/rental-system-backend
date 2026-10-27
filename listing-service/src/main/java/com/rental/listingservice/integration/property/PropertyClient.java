package com.rental.listingservice.integration.property;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "property-service", configuration = {
        FeignDefaultConfig.class, PropertyInternalFeignConfig.class
})
public interface PropertyClient {

    @GetMapping("/internal/rooms/{roomId}/listing-snapshot")
    ListingRoomSnapshot getListingSnapshot(@PathVariable("roomId") Long roomId);
}
