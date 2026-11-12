package com.rental.contracttenant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI contractTenantServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Room Rental Contract & Tenant Service API")
                        .description("API quản lý vòng đời hợp đồng thuê phòng, chữ ký điện tử, người ở cùng phòng, "
                                + "yêu cầu gia hạn và yêu cầu trả phòng/hoàn cọc. Phân trang dùng PageResponse; lỗi dùng ErrorResponse của common-lib.")
                        .version("v1"))
                .tags(List.of(
                        new Tag().name("Hợp đồng thuê").description("Soạn thảo, gửi ký, ký điện tử, tải PDF, gia hạn trực tiếp và thanh lý hợp đồng"),
                        new Tag().name("Người ở trong hợp đồng").description("Thêm người ở, đổi người đại diện và ghi nhận người rời phòng"),
                        new Tag().name("Gia hạn hợp đồng").description("Khách gửi yêu cầu gia hạn; quản lý duyệt, từ chối và tạo nháp gia hạn"),
                        new Tag().name("Trả phòng & Hoàn cọc").description("Yêu cầu trả phòng, duyệt trả phòng, chứng từ và quyết toán hoàn cọc")));
    }
}

