package com.rental.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.common.constant.Headers;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** Unit test JwtAuthenticationFilter bang Mock exchange, khong can Netty/Eureka (Task D2). */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "gateway-test-secret-at-least-32-chars-00";
    private static final String OTHER_SECRET = "another-gateway-secret-32-chars-min-000";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET, "rental-auth",
            new GatewayWhitelist());

    @Test
    void register_khongCanToken_duocChuyenTiep() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/v1/auth/register", null, null);
        GatewayFilterChain chain = chain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void me_khongToken_401() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/auth/me", null, null);

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(body(exchange)).contains("UNAUTHENTICATED");
    }

    @Test
    void tokenHetHan_401TokenExpired() {
        String expired = token("7", "CUSTOMER", List.of("CUSTOMER"), -60_000);

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/auth/me", "Bearer " + expired, null);

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(body(exchange)).contains("TOKEN_EXPIRED");
    }

    @Test
    void tokenSaiChuKy_401() {
        String forged = Jwts.builder().subject("7").claim("typ", "ADMIN").claim("roles", List.of("ADMIN"))
                .issuer("rental-auth").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8))).compact();

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/auth/me", "Bearer " + forged, null);

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(body(exchange)).contains("UNAUTHENTICATED");
    }

    @Test
    void headerGia_biXoaVaThayBangThat() {
        String token = token("7", "CUSTOMER", List.of("CUSTOMER"), 600_000);
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/auth/me", "Bearer " + token, "999");
        GatewayFilterChain chain = chain();
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> next = ArgumentCaptor
                .forClass(org.springframework.web.server.ServerWebExchange.class);

        filter.filter(exchange, chain).block();

        verify(chain).filter(next.capture());
        var headers = next.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst(Headers.X_USER_ID)).isEqualTo("7");
        assertThat(headers.getFirst(Headers.X_USER_TYPE)).isEqualTo("CUSTOMER");
        assertThat(headers.getFirst(Headers.X_USER_ROLES)).isEqualTo("CUSTOMER");
    }

    @Test
    void tokenNhieuRole_noiBangPhay() {
        String token = token("3", "STAFF", List.of("QUAN_LY", "SALE"), 600_000);
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/auth/me", "Bearer " + token, null);
        GatewayFilterChain chain = chain();
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> next = ArgumentCaptor
                .forClass(org.springframework.web.server.ServerWebExchange.class);

        filter.filter(exchange, chain).block();

        verify(chain).filter(next.capture());
        assertThat(next.getValue().getRequest().getHeaders().getFirst(Headers.X_USER_ROLES))
                .isEqualTo("QUAN_LY,SALE");
    }

    @Test
    void internal_biChan_404() {
        String token = token("1", "ADMIN", List.of("ADMIN"), 600_000);
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/internal/staff/1/buildings", "Bearer " + token,
                null);

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void listingsPublic_duocChuyenTiep() {
        MockServerWebExchange list = exchange(HttpMethod.GET, "/api/v1/listings", null, null);
        MockServerWebExchange detail = exchange(HttpMethod.GET, "/api/v1/listings/abc", null, null);
        MockServerWebExchange nearby = exchange(HttpMethod.GET, "/api/v1/buildings/nearby", null, null);

        filter.filter(list, chain()).block();
        filter.filter(detail, chain()).block();
        filter.filter(nearby, chain()).block();

        assertThat(list.getResponse().getStatusCode()).isNull();
        assertThat(detail.getResponse().getStatusCode()).isNull();
        assertThat(nearby.getResponse().getStatusCode()).isNull();
    }

    @Test
    void secretNgan_failFast() {
        assertThatThrownBy(() -> new JwtAuthenticationFilter("short", "rental-auth", new GatewayWhitelist()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, String authorization,
            String fakeUserId) {
        var builder = MockServerHttpRequest.method(method, "http://localhost" + path);
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (fakeUserId != null) {
            builder.header(Headers.X_USER_ID, fakeUserId);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static GatewayFilterChain chain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    private static String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    private static String token(String sub, String typ, List<String> roles, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(sub).claim("typ", typ).claim("roles", roles).issuer("rental-auth")
                .issuedAt(new Date(now)).expiration(new Date(now + ttlMillis)).id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}