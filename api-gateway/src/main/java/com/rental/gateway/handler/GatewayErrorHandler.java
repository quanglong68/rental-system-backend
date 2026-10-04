package com.rental.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.ErrorResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Loi chuan Gateway (docs muc 8, Task D3): moi loi khong duoc filter xu ly
 * (no-route 404, LB khong thay service 503, ...) deu tra dung format
 * {code,message,status,traceId} nhu common-lib (traceId = correlationId).
 * Ghi de DefaultErrorWebExceptionHandler cua Boot (order thap hon).
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = statusOf(exchange, ex);
        ErrorCode code;
        String message;
        if (status == HttpStatus.NOT_FOUND) {
            code = ErrorCode.NOT_FOUND;
            message = "Khong tim thay";
        } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            code = ErrorCode.DEPENDENCY_UNAVAILABLE;
            message = "Service phu thuoc khong phan hoi";
        } else if (status == HttpStatus.UNAUTHORIZED) {
            code = ErrorCode.UNAUTHENTICATED;
            message = "Chua dang nhap";
        } else {
            code = ErrorCode.INTERNAL_ERROR;
            message = "Loi he thong, vui long thu lai sau";
        }
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

    private static HttpStatus statusOf(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof ResponseStatusException statusEx) {
            return HttpStatus.resolve(statusEx.getStatusCode().value());
        }
        if (exchange.getResponse().getStatusCode() instanceof HttpStatus set) {
            return set;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}