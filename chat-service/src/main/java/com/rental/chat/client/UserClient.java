package com.rental.chat.client;

import com.rental.common.feign.FeignDefaultConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-notification-service", configuration = FeignDefaultConfig.class)
public interface UserClient {

    record CustomerSummary(Long id, String fullName) {
    }

    record StaffSummary(Long id, String fullName, String position, Boolean isAdmin) {
    }

    @GetMapping("/internal/customers")
    List<CustomerSummary> customers(@RequestParam("ids") List<Long> ids);

    @GetMapping("/internal/staff")
    List<StaffSummary> staff(@RequestParam("ids") List<Long> ids);

    @GetMapping("/internal/staff/by-building/{buildingId}")
    List<StaffSummary> staffByBuilding(@PathVariable("buildingId") Long buildingId);
}
