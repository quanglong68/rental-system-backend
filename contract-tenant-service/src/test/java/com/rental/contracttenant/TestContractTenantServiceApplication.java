package com.rental.contracttenant;

import org.springframework.boot.SpringApplication;

public class TestContractTenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(ContractTenantServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
