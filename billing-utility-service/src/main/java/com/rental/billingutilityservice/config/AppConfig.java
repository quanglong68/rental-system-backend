package com.rental.billingutilityservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.rental.common.error.GlobalExceptionHandler;
import com.rental.common.feign.FeignHeaderInterceptor;
import feign.RequestInterceptor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.TimeUnit;

/**
 * Cau hinh chung billing-service: GlobalExceptionHandler (format loi muc 8),
 * FeignHeaderInterceptor (sao chep header muc 5.2), cache pham vi toa nha 5 phut,
 * error handler Kafka (thu lai 3 lan / 1s, loi day sang DLT - muc 5.3).
 */
@Configuration
@Import(GlobalExceptionHandler.class)
public class AppConfig {

    @Bean
    public RequestInterceptor feignHeaderInterceptor() {
        return new FeignHeaderInterceptor();
    }

    /** Cache staff-buildings 5 phut cho cuoc goi Feign #5 (docs muc 5.2). */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("staffBuildings");
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(5000));
        return manager;
    }

    /** Loi consumer: thu lai 3 lan cach nhau 1s, van loi day sang <topic>.DLT (docs muc 5.3). */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(org.springframework.kafka.core.KafkaTemplate<String, String> kafka) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> kafka.send(record.topic() + ".DLT", (String) record.key(),
                        (String) record.value()),
                new FixedBackOff(1000L, 3L));
        handler.setCommitRecovered(true);
        return handler;
    }
}
