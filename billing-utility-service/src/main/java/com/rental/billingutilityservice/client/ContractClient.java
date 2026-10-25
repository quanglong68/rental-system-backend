package com.rental.billingutilityservice.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign #2 (docs muc 5.2): Billing goi Contract lay hop dong active + nguoi o khi chot so.
 * Khi loi: job dung, ghi log, staff chay lai (khong sinh hoa don thieu).
 */
@FeignClient(name = "contract-tenant-service", configuration = FeignDefaultConfig.class)
public interface ContractClient {

    @GetMapping("/internal/contracts/billing-data")
    List<BillingDataResponse> getBillingData(@RequestParam("month") int month,
                                             @RequestParam("year") int year);
}
