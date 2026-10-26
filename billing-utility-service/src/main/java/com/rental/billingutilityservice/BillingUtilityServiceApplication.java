package com.rental.billingutilityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Billing-utility-service (docs muc 9.5, port 8086): dien nuoc, dich vu, hoa don, thanh toan.
 * Feign goi Contract (#2), User (#5), Property (#6) (docs muc 5.2).
 * Scheduling cho chot so hang thang + quet overdue (docs muc 9.5).
 * Caching cho ket qua kiem tra pham vi toa nha (Feign #5, TTL 5 phut).
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.rental.billingutilityservice.client")
@EnableScheduling
@EnableCaching
public class BillingUtilityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingUtilityServiceApplication.class, args);
    }
}
