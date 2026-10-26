package fptu.semester8.mss301.propertiesservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    OpenAPI propertiesServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Room Rental Properties Service API")
                .description("API quản lý tòa nhà, phòng cho thuê, loại phòng, đơn vị hành chính và tải ảnh. "
                        + "Các endpoint dữ liệu trả về ApiResponse; endpoint danh sách có thể trả Page khi được phân trang.")
                .version("v1"));
    }
}
