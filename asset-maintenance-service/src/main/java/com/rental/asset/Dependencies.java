package com.rental.asset;
import java.util.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import com.rental.common.feign.FeignDefaultConfig;

@FeignClient(name="property-service",configuration=FeignDefaultConfig.class) interface PropertyClient {
 @GetMapping("/internal/rooms/{roomId}") RoomInfo getRoom(@PathVariable Long roomId);
 record RoomInfo(Long id, Long buildingId) {}
}
@FeignClient(name="contract-tenant-service",configuration=FeignDefaultConfig.class) interface ContractClient {
 @GetMapping("/internal/rooms/{roomId}/tenants/{customerId}") TenantInfo tenant(@PathVariable Long roomId,@PathVariable Long customerId);
 record TenantInfo(boolean isTenant, Long contractId, Long buildingId) {}
}
@FeignClient(name="user-notification-service",configuration=FeignDefaultConfig.class) interface UserClient {
 @GetMapping("/internal/staff/{staffId}/buildings") BuildingResponse staffBuildings(@PathVariable Long staffId);
 record BuildingResponse(Long staffId,List<Long> buildingIds) {}
}
