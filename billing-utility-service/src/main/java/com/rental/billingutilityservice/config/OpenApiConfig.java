package com.rental.billingutilityservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI billingUtilityServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Room Rental Billing & Utility Service API")
                .description("API quản lý loại tiện ích, đồng hồ và chốt chỉ số điện nước, dịch vụ tòa nhà/hợp đồng, "
                        + "hóa đơn thành viên/tổng phòng và thanh toán (Tiền mặt, VNPay Sandbox). Phân trang dùng PageResponse; "
                        + "lỗi dùng ErrorResponse của common-lib.")
                .version("v1"));
    }
}

