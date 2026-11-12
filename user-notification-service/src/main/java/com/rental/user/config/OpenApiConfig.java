package com.rental.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI userNotificationServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Room Rental User & Notification Service API")
                        .description("API quản lý hồ sơ khách hàng, hồ sơ nhân sự, phân công tòa nhà cho nhân sự, "
                                + "thông báo người dùng và thiết bị nhận push. Phân trang dùng PageResponse; lỗi dùng ErrorResponse của common-lib.")
                        .version("v1"))
                .tags(List.of(
                        new Tag().name("Hồ sơ người dùng").description("Xem, cập nhật hồ sơ cá nhân và tra cứu danh sách khách hàng"),
                        new Tag().name("Nhân sự & Phân công").description("Quản lý nhân sự và phân công phạm vi tòa nhà phụ trách"),
                        new Tag().name("Thông báo").description("Danh sách thông báo, số lượng chưa đọc và đánh dấu đã đọc"),
                        new Tag().name("Thiết bị Push").description("Đăng ký và hủy token thiết bị nhận push notification")));
    }
}

