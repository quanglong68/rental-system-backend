package com.rental.asset.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI assetMaintenanceServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Room Rental Asset & Maintenance Service API")
                        .description("API quản lý thiết bị/nội thất phòng, thông tin bảo hành, báo hỏng từ khách thuê, "
                                + "yêu cầu sửa chữa kỹ thuật và lịch bảo trì định kỳ.")
                        .version("v1"))
                .tags(List.of(
                        new Tag().name("Thiết bị & Nội thất").description("Tạo, tra cứu, cập nhật và quản lý bảo hành thiết bị trong phòng"),
                        new Tag().name("Báo hỏng từ khách thuê").description("Xin URL upload ảnh, gửi và duyệt/từ chối báo hỏng thiết bị"),
                        new Tag().name("Yêu cầu sửa chữa").description("Phân công kỹ thuật viên, bắt đầu và hoàn tất xử lý sửa chữa"),
                        new Tag().name("Lịch bảo trì định kỳ").description("Lập lịch, cập nhật, hoàn tất chu kỳ và ngưng lịch bảo trì")));
    }
}

