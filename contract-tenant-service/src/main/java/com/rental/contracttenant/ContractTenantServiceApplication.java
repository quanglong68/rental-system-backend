package com.rental.contracttenant;

import com.rental.common.error.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class ContractTenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractTenantServiceApplication.class, args);
    }

}
