package com.rental.gateway.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

/** Unit test GatewayErrorHandler: moi loi ve dung format ErrorResponse (Task D3). */
class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler = new GatewayErrorHandler();

    @Test
    void noRoute_404NotFound() {
        MockServerWebExchange exchange = exchange("/khong-co-route");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching route")).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(404);
        assertThat(body(exchange)).contains("NOT_FOUND");
    }

    @Test
    void lbKhongThayService_503DependencyUnavailable() {
        MockServerWebExchange exchange = exchange("/api/v1/auth/me");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LB failed")).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(body(exchange)).contains("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void loiLa_500InternalError() {
        MockServerWebExchange exchange = exchange("/api/v1/auth/me");

        handler.handle(exchange, new RuntimeException("boom")).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(500);
        assertThat(body(exchange)).contains("INTERNAL_ERROR");
    }

    @Test
    void traceId_layTuCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.GET, "http://localhost/api/v1/auth/me").header("X-Correlation-Id", "cid-123"));

        handler.handle(exchange, new RuntimeException("boom")).block();

        assertThat(body(exchange)).contains("cid-123");
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange
                .from(MockServerHttpRequest.method(HttpMethod.GET, "http://localhost" + path));
    }

    private static String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}