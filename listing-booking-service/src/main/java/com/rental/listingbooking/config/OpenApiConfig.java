package com.rental.listingbooking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI listingBookingServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Room Rental Listing & Booking Service API")
                        .description("API tin đăng cho thuê phòng, yêu cầu thuê phòng (kèm hồ sơ CCCD), "
                                + "đặt lịch hẹn xem phòng và tìm bạn ở ghép. Phân trang dùng PageResponse; lỗi dùng ErrorResponse của common-lib.")
                        .version("v1"))
                .tags(List.of(
                        new Tag().name("Tin đăng cho thuê").description("Tìm kiếm công khai, xem chi tiết và cập nhật trạng thái tin đăng phòng"),
                        new Tag().name("Yêu cầu thuê phòng").description("Khách gửi/hủy yêu cầu thuê, tải CCCD và nhân sự xác minh/duyệt/từ chối"),
                        new Tag().name("Lịch hẹn xem phòng").description("Đặt lịch, tra cứu và cập nhật trạng thái lịch hẹn xem phòng"),
                        new Tag().name("Tìm bạn ở ghép").description("Đăng tin, tìm kiếm và đóng tin tìm người ở ghép"),
                        new Tag().name("Đơn ứng tuyển ở ghép").description("Ứng tuyển vào phòng đang ở ghép, duyệt/từ chối và xem CCCD ứng viên")));
    }
}

