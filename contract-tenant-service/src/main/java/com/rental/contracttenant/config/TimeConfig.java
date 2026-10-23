package com.rental.contracttenant.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Business "today" is defined in Vietnam time (spec 9.4: scheduler 00:10 giờ Việt Nam), independent of
 * the JVM default zone. Injected so tests can pin the instant.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock businessClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
