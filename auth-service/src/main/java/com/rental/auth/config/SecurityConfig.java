package com.rental.auth.config;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Bao mat auth-service (docs muc 6.2, 7.1): stateless, service doc header Gateway
 * (khong verify JWT lai), phan quyen bang @PreAuthorize (can @EnableMethodSecurity).
 * Whitelist PUBLIC theo Table 7: register, login, refresh (+ health, swagger cho dev).
 * Loi 401/403 tra JSON dung format ErrorResponse (filter chay truoc controller
 * nen @RestControllerAdvice khong bat duoc, phai viet thang o entryPoint/handler).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Băm mat khau BCrypt cost 10 (docs muc 7.1). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ERROR dispatch (vd 404 do chua co controller) khong mang SecurityContext
                        // (OncePerRequestFilter bo qua + Anonymous chay lai) nen cho qua de hien dung ma loi
                        // thay vi entryPoint 401 gay hieu lam.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, PublicPaths.POST).permitAll()
                        .requestMatchers(HttpMethod.GET, PublicPaths.GET).permitAll().anyRequest().authenticated())
                .addFilterBefore(new AuthHeaderAuthenticationFilter(), AuthorizationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> writeError(request, response,
                                HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.name(),
                                "Chua dang nhap", objectMapper))
                        .accessDeniedHandler((request, response, ex) -> writeError(request, response,
                                HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN.name(), "Khong du quyen",
                                objectMapper)))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private static void writeError(HttpServletRequest request, HttpServletResponse response, int status, String code,
            String message, ObjectMapper objectMapper) throws IOException {
        String correlationId = request.getHeader(Headers.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        ErrorResponse body = ErrorResponse.builder().code(code).message(message).status(status)
                .traceId(correlationId).build();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
