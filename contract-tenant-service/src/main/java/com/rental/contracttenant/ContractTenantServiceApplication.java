package com.rental.contracttenant;

import com.rental.common.error.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} drives the outbox relay (docs muc 5.3). */
@SpringBootApplication
@EnableScheduling
@Import(GlobalExceptionHandler.class)
public class ContractTenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractTenantServiceApplication.class, args);
    }

}
