package com.rental.contracttenant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "eureka.client.enabled=false"
})
class ContractTenantServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
