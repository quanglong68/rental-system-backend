package com.rental.chat.config;

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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/internal/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new ChatHeaderAuthenticationFilter(), AuthorizationFilter.class)
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

    private static final class ChatHeaderAuthenticationFilter extends HeaderAuthenticationFilter {
        @Override
        protected boolean isSkipped(String requestUri) {
            return super.isSkipped(requestUri)
                    || (requestUri != null && (requestUri.startsWith("/v3/api-docs")
                            || requestUri.startsWith("/swagger-ui")));
        }
    }
}
