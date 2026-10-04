package com.rental.gateway.filter;

import com.rental.common.constant.Headers;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Dam bao moi request co X-Correlation-Id de truy vet xuyen service (docs muc 2, Task D2).
 * Chay dau tien (order thap nhat) de filter phia sau (JWT, log) luon co id.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    public int getOrder() {
        return -101;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(Headers.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;
        var mutated = exchange.getRequest().mutate().headers(headers -> {
            if (!headers.containsKey(Headers.X_CORRELATION_ID)) {
                headers.set(Headers.X_CORRELATION_ID, finalCorrelationId);
            }
        }).build();
        log.info("Gateway {} {} [{}]", exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                finalCorrelationId);
        return chain.filter(exchange.mutate().request(mutated).build()).doFinally(signal -> log.info(
                "Gateway {} {} -> {} [{}]", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(), exchange.getResponse().getStatusCode(),
                finalCorrelationId));
    }
}