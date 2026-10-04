package com.rental.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Xac thuc JWT tai Gateway dung thu tu docs muc 6.1 (Task D2):
 * 1. Chan moi /internal/** tu ben ngoai -> 404 (kenh noi bo giua service).
 * 2. Xoa X-User-Id/X-User-Type/X-User-Roles client tu gui (chong gia mao).
 * 3. Whitelist cho qua khong can token (dang nhap + du lieu cong khai).
 * 4. Con lai: thieu/sai Authorization Bearer -> 401 UNAUTHENTICATED;
 *    verify HS256 + iss + exp: het han -> 401 TOKEN_EXPIRED.
 * 5. Hop le -> gan X-User-Id=sub, X-User-Type=typ, X-User-Roles=roles (join ",")
 *    roi forward. Gateway khong gom du lieu, khong logic nghiep vu.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey key;
    private final String issuer;
    private final GatewayWhitelist whitelist;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer:rental-auth}") String issuer, GatewayWhitelist whitelist) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("jwt.secret phai toi thieu 32 ky tu (HS256 can khoa 256-bit)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = issuer;
        this.whitelist = whitelist;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getPath().value();
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            return writeError(exchange, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Khong tim thay");
        }
        var mutated = request.mutate().headers(headers -> {
            headers.remove(Headers.X_USER_ID);
            headers.remove(Headers.X_USER_TYPE);
            headers.remove(Headers.X_USER_ROLES);
        }).build();
        if (whitelist.isPublic(request.getMethod(), path)) {
            return chain.filter(exchange.mutate().request(mutated).build());
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Chua dang nhap");
        }
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).clockSkewSeconds(30).build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length())).getPayload();
        } catch (ExpiredJwtException expired) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_EXPIRED, "Token da het han");
        } catch (JwtException | IllegalArgumentException invalid) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Token khong hop le");
        }
        List<?> roles = claims.get("roles", List.class);
        String rolesHeader = roles == null ? ""
                : roles.stream().map(String::valueOf).collect(Collectors.joining(","));
        var authed = mutated.mutate().headers(headers -> {
            headers.set(Headers.X_USER_ID, claims.getSubject());
            headers.set(Headers.X_USER_TYPE, claims.get("typ", String.class));
            headers.set(Headers.X_USER_ROLES, rolesHeader);
        }).build();
        return chain.filter(exchange.mutate().request(authed).build());
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, ErrorCode code, String message) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(Headers.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        ErrorResponse body = ErrorResponse.builder().code(code.name()).message(message).status(status.value())
                .traceId(correlationId).build();
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException impossible) {
            bytes = ("{\"code\":\"" + code.name() + "\",\"status\":" + status.value() + "}").getBytes(
                    StandardCharsets.UTF_8);
        }
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}