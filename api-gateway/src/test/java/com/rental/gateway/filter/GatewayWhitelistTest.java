package com.rental.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class GatewayWhitelistTest {

    private final GatewayWhitelist whitelist = new GatewayWhitelist();

    @Test
    void swaggerUiVaOpenApiDocsChoPhepGetKhongCanToken() {
        for (String path : List.of("/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui.css",
                "/v3/api-docs", "/v3/api-docs/swagger-config", "/v3/api-docs/property-service",
                "/v3/api-docs/listing-booking-service", "/swagger-config")) {
            assertThat(whitelist.isPublic(HttpMethod.GET, path)).as(path).isTrue();
            assertThat(whitelist.isPublic(HttpMethod.POST, path)).as(path).isFalse();
        }
    }

    @Test
    void minioImagesChoPhepMoiMethodKhongCanToken() {
        for (String path : List.of("/property-images/rooms/1.jpg", "/property-images/buildings/2.png",
                "/report-images/incident-1.webp")) {
            assertThat(whitelist.isPublic(HttpMethod.GET, path)).as(path).isTrue();
            assertThat(whitelist.isPublic(HttpMethod.PUT, path)).as(path).isTrue();
            assertThat(whitelist.isPublic(HttpMethod.HEAD, path)).as(path).isTrue();
        }
    }

    @Test
    void vnpayEndpointsChoPhepMoiMethodKhongCanToken() {
        for (String path : List.of("/api/v1/payments/vnpay-ipn", "/api/v1/payments/vnpay-ipn/",
                "/api/v1/payments/vnpay/confirm", "/api/v1/payments/vnpay/confirm/")) {
            assertThat(whitelist.isPublic(HttpMethod.GET, path)).as(path).isTrue();
            assertThat(whitelist.isPublic(HttpMethod.POST, path)).as(path).isTrue();
        }
    }
}
