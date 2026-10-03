package com.rental.common.feign;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;

class FeignDefaultConfigTest {

    private final FeignDefaultConfig config = new FeignDefaultConfig();

    @Test
    void timeout_dung2000Va3000() {
        assertThat(FeignDefaultConfig.CONNECT_TIMEOUT_MS).isEqualTo(2000);
        assertThat(FeignDefaultConfig.READ_TIMEOUT_MS).isEqualTo(3000);
        Request.Options options = config.feignOptions();

        assertThat(options.connectTimeoutMillis()).isEqualTo(2000);
        assertThat(options.readTimeoutMillis()).isEqualTo(3000);
    }

    @Test
    void bean_dungLoai() {
        assertThat(config.feignRetryer()).isInstanceOf(GetOnlyRetryer.class);
        assertThat(config.feignRetryer()).isNotSameAs(config.feignRetryer());
        assertThat(config.feignErrorDecoder()).isInstanceOf(ErrorDecoder.class);
        Customizer<Resilience4JCircuitBreakerFactory> customizer = config.feignCircuitBreakerCustomizer();
        assertThat(customizer).isNotNull();
        assertThat(Retryer.class).isAssignableFrom(GetOnlyRetryer.class);
    }

    @Test
    void circuitBreaker_dung10Call50Pct10s() {
        var cb = FeignDefaultConfig.defaultCircuitBreakerConfig();

        assertThat(cb.getSlidingWindowSize()).isEqualTo(10);
        assertThat(cb.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(cb.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(10_000L);
    }
}
