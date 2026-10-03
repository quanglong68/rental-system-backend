package com.rental.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server dung chung (docs muc 4.1).
 * Moi service khai bao spring.application.name dung ten Table 1 va tu dang ky khi start;
 * goi nhau bang ten logic, Gateway dinh tuyen bang lb://&lt;ten-service&gt;.
 */
@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
