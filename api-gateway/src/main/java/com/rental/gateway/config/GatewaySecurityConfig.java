package com.rental.gateway.config;

import com.rental.gateway.filter.GatewayWhitelist;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cau hinh bao mat Gateway (docs muc 6.1, Task D2): CORS khai bao trong application.yml
 * (Task D3); chan /internal/** tu ben ngoai va xac thuc JWT nam trong
 * {@link com.rental.gateway.filter.JwtAuthenticationFilter}.
 * Class nay tap trung whitelist PUBLIC de filter tai su dung.
 */
@Configuration
public class GatewaySecurityConfig {

    @Bean
    public GatewayWhitelist gatewayWhitelist() {
        return new GatewayWhitelist();
    }
}