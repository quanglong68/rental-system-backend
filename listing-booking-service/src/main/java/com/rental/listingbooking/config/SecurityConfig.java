package com.rental.listingbooking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.ErrorResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATHS = {
        "/api/v1/listings",
        "/api/v1/listings/{id:[0-9]+}",
        "/api/v1/listings/room/{roomId:[0-9]+}",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new ListingHeaderAuthenticationFilter(), AuthorizationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> writeError(request, response,
                                HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.name(),
                                "Chưa đăng nhập", objectMapper))
                        .accessDeniedHandler((request, response, ex) -> writeError(request, response,
                                HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN.name(), "Không đủ quyền",
                                objectMapper)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private static void writeError(HttpServletRequest request, HttpServletResponse response, int status, String code,
            String message, ObjectMapper objectMapper) throws IOException {
        String correlationId = request.getHeader(Headers.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .code(code)
                .message(message)
                .status(status)
                .traceId(correlationId)
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
