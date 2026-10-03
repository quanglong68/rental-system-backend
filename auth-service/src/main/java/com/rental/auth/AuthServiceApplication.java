package com.rental.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Identity &amp; Auth Service (docs muc 7, port 8081).
 * Dang ky CUSTOMER, tao STAFF/ADMIN, dang nhap, cap JWT (15 phut) + refresh token (7 ngay),
 * phat event AccountRegistered qua Transactional Outbox.
 * CORS mo o Gateway, service nay khong mo CORS.
 */
@EnableScheduling
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
