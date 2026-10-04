package com.rental.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway: cong vao duy nhat (docs muc 4.2, port 8080).
 * Xac thuc JWT, gan header X-User-*, dinh tuyen theo prefix (Table 2).
 * Khong gom du lieu, khong logic nghiep vu (docs muc 6.1).
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}