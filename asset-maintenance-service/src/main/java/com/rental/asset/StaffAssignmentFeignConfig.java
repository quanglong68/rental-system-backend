package com.rental.asset;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** Credential only for the staff-building lookup, never for general client requests. */
public class StaffAssignmentFeignConfig {
    @Bean
    RequestInterceptor staffAssignmentCredential(@Value("${staff.assignment.internal-key:}") String key) {
        return request -> {
            if (key.length() >= 32) request.header("X-Staff-Assignment-Key", key);
        };
    }
}
