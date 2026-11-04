package com.rental.propertiesservice;

import com.rental.common.error.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@Import(GlobalExceptionHandler.class)
public class PropertiesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertiesServiceApplication.class, args);
    }

}
