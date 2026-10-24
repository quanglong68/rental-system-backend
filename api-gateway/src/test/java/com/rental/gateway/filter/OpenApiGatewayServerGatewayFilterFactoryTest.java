package com.rental.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenApiGatewayServerGatewayFilterFactoryTest {

    private final ObjectMapper json = new ObjectMapper();
    private final OpenApiGatewayServerGatewayFilterFactory filter =
            new OpenApiGatewayServerGatewayFilterFactory(null, json, new GatewayWhitelist());

    @Test
    void replacesContainerServerAndKeepsPublicPaths() throws Exception {
        String source = """
                {"openapi":"3.0.1","servers":[{"url":"http://172.19.0.6:8081"}],
                 "paths":{"/api/v1/auth/login":{"post":{}},
                          "/api/v1/auth/me":{"get":{}},
                          "/internal/contracts":{"post":{}}}}
                """;

        var rewritten = json.readTree(filter.rewrite(source));

        assertThat(rewritten.path("servers").get(0).path("url").asText()).isEqualTo("/");
        assertThat(rewritten.path("paths").has("/api/v1/auth/login")).isTrue();
        assertThat(rewritten.path("paths").has("/internal/contracts")).isFalse();
        assertThat(rewritten.path("components").path("securitySchemes")
                .path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(rewritten.path("paths").path("/api/v1/auth/login")
                .path("post").has("security")).isFalse();
        assertThat(rewritten.path("paths").path("/api/v1/auth/me")
                .path("get").path("security").get(0).has("bearerAuth")).isTrue();
    }

    @Test
    void doesNotChangeGatewayErrors() {
        String error = "{\"status\":503,\"message\":\"Service unavailable\"}";
        assertThat(filter.rewrite(error)).isEqualTo(error);
    }

    @Test
    void keepsExistingSecurityAndLeavesPublicReadsUnlocked() throws Exception {
        String source = """
                {"openapi":"3.0.1",
                 "components":{"securitySchemes":{"apiKey":{"type":"apiKey","in":"header","name":"X-API-Key"}}},
                 "paths":{"/api/v1/rooms":{"get":{"security":[{"apiKey":[]}]}},
                          "/api/v1/buildings/nearby":{"get":{}}}}
                """;

        var rewritten = json.readTree(filter.rewrite(source));
        var roomSecurity = rewritten.path("paths").path("/api/v1/rooms")
                .path("get").path("security").get(0);

        assertThat(roomSecurity.has("apiKey")).isTrue();
        assertThat(roomSecurity.has("bearerAuth")).isTrue();
        assertThat(rewritten.path("paths").path("/api/v1/buildings/nearby")
                .path("get").has("security")).isFalse();
    }
}
