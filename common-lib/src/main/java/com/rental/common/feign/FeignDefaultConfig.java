package com.rental.common.feign;

import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cau hinh Feign mac dinh cho moi service (docs muc 5.2).
 * Cach dung tren interface goi: {@code @FeignClient(name = "property-service",
 * configuration = FeignDefaultConfig.class)} (interface dat trong package client,
 * DTO response do ben goi tu khai bao field minh can, endpoint {@code /internal/**}).
 * Ten service do Eureka phan giai, LoadBalancer chon instance.
 */
@Configuration
public class FeignDefaultConfig {

    /** Timeout ket noi (docs muc 5.2). */
    public static final long CONNECT_TIMEOUT_MS = 2000;

    /** Timeout doc response (docs muc 5.2). */
    public static final long READ_TIMEOUT_MS = 3000;

    @Bean
    public Request.Options feignOptions() {
        return new Request.Options(Duration.ofMillis(CONNECT_TIMEOUT_MS), Duration.ofMillis(READ_TIMEOUT_MS), true);
    }

    @Bean
    public Retryer feignRetryer() {
        return new GetOnlyRetryer();
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }

    /** Mach ngat mac dinh: cua so 10 cuoc goi, nguong loi 50%, mo mach 10 giay (docs muc 5.2). */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> feignCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(
                id -> new Resilience4JConfigBuilder(id).circuitBreakerConfig(defaultCircuitBreakerConfig()).build());
    }

    static CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom().slidingWindowSize(10).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10)).build();
    }
}
