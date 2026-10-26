package com.rental.contracttenant;

import com.rental.common.error.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} drives the outbox relay (docs muc 5.3);
 * {@code @EnableFeignClients} registers the client package (Feign calls #1, #5, #8, docs muc 5.2).
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients
@Import(GlobalExceptionHandler.class)
public class ContractTenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractTenantServiceApplication.class, args);
    }

}
