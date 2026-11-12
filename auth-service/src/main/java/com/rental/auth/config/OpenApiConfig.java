package com.rental.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI authServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Room Rental Auth Service API")
                .description("API xác thực người dùng (đăng ký, đăng nhập, làm mới token, đăng xuất, đổi mật khẩu) "
                        + "và quản trị tài khoản hệ thống. Lỗi theo chuẩn ErrorResponse {code,message,status,traceId,details} của common-lib.")
                .version("v1"));
    }
}

