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
    void propertyPublicContractIsLimitedToDetailsAndRoomTypes() {
        for (String path : List.of("/api/v1/provinces", "/api/v1/provinces/1", "/api/v1/wards",
                "/api/v1/wards/province/1", "/api/v1/buildings/nearby", "/api/v1/buildings/123",
                "/api/v1/room-types")) {
            assertThat(whitelist.isPublic(HttpMethod.GET, path)).as(path).isTrue();
            assertThat(whitelist.isPublic(HttpMethod.POST, path)).as(path).isFalse();
        }
        for (String path : List.of("/api/v1/buildings", "/api/v1/buildings/search", "/api/v1/buildings/abc",
                "/api/v1/room-types/1")) {
            assertThat(whitelist.isPublic(HttpMethod.GET, path)).as(path).isFalse();
        }
    }
}
