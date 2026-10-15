package com.rental.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.ErrorResponse;
import com.rental.common.security.HeaderAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration @EnableWebSecurity @EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/internal/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll().anyRequest().authenticated())
                .addFilterBefore(new HeaderAuthenticationFilter() {
                    @Override protected boolean isSkipped(String path) {
                        return super.isSkipped(path) || path != null && (path.startsWith("/v3/api-docs")
                                || path.startsWith("/swagger-ui"));
                    }
                }, AuthorizationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> writeError(req, res, 401,
                                ErrorCode.UNAUTHENTICATED.name(), "Chua dang nhap", mapper))
                        .accessDeniedHandler((req, res, ex) -> writeError(req, res, 403,
                                ErrorCode.FORBIDDEN.name(), "Khong du quyen", mapper)))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).build();
    }

    private static void writeError(HttpServletRequest request, HttpServletResponse response, int status, String code,
            String message, ObjectMapper mapper) throws IOException {
        String trace = request.getHeader(Headers.X_CORRELATION_ID);
        if (trace == null || trace.isBlank()) trace = UUID.randomUUID().toString();
        ErrorResponse body = ErrorResponse.builder().code(code).message(message).status(status).traceId(trace).build();
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8"); mapper.writeValue(response.getWriter(), body);
    }
}
