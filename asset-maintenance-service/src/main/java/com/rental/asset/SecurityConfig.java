package com.rental.asset;

import com.rental.common.security.HeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration @EnableWebSecurity @EnableMethodSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll().anyRequest().authenticated())
            .addFilterBefore(new HeaderAuthenticationFilter() {
                @Override protected boolean isSkipped(String path) { return super.isSkipped(path) || path != null && (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")); }
            }, AuthorizationFilter.class).httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).build();
    }
}
