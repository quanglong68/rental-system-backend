package com.rental.listingservice.integration.property;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class PropertyInternalFeignConfig {

    @Bean
    RequestInterceptor propertyInternalCredential(@Value("${property.internal-key:}") String key) {
        return request -> {
            if (key.length() >= 32) {
                request.header("X-Property-Internal-Key", key);
            }
        };
    }
}
