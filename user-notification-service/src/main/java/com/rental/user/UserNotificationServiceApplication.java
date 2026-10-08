package com.rental.user;

import com.rental.common.error.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class UserNotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserNotificationServiceApplication.class, args);
    }
}
