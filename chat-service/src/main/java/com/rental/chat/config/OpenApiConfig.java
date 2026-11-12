package com.rental.chat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI chatServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Room Rental Chat Service API")
                .description("API nhắn tin realtime giữa khách hàng và nhân sự theo ngữ cảnh tin đăng/yêu cầu thuê/hợp đồng, "
                        + "hỗ trợ phân trang cursor và luồng Server-Sent Events (SSE).")
                .version("v1"));
    }
}

