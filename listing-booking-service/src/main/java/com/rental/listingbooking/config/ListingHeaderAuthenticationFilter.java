package com.rental.listingbooking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.ErrorResponse;
import com.rental.common.security.HeaderAuthenticationFilter;
import com.rental.common.security.HeaderUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;

public class ListingHeaderAuthenticationFilter extends HeaderAuthenticationFilter {

    private static final String[] PUBLIC_GET_PATHS = {
        "/api/v1/listings",
        "/api/v1/listings/{id:[0-9]+}",
        "/api/v1/listings/room/{roomId:[0-9]+}",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/**"
    };

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(Headers.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_CORRELATION_ID, correlationId);

        try {
            String uri = request.getRequestURI();
            String method = request.getMethod();

            // 1. Internal service-to-service calls or actuator/swagger
            if (uri != null && (uri.startsWith("/internal") || uri.startsWith("/actuator") || uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui"))) {
                chain.doFilter(request, response);
                return;
            }

            String userId = request.getHeader(Headers.X_USER_ID);
            String userType = request.getHeader(Headers.X_USER_TYPE);

            // 2. If user headers are provided (authenticated through Gateway), always set security context
            if (userId != null && !userId.isBlank() && userType != null && !userType.isBlank()) {
                List<String> roles = rolesOf(request.getHeader(Headers.X_USER_ROLES));
                HeaderUser principal = new HeaderUser(userId.trim(), userType.trim(), roles);
                MDC.put(MDC_USER_ID, principal.getUserId());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, authoritiesOf(roles));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                chain.doFilter(request, response);
                return;
            }

            // 3. If no user headers, only permit GET requests on public endpoints
            if (HttpMethod.GET.matches(method) && isPublicGetPath(uri)) {
                chain.doFilter(request, response);
                return;
            }

            // 4. Otherwise, reject as unauthenticated
            writeUnauthenticated(response, correlationId);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    private boolean isPublicGetPath(String uri) {
        if (uri == null) return false;
        return Arrays.stream(PUBLIC_GET_PATHS).anyMatch(p -> matcher.match(p, uri));
    }

    private static List<String> rolesOf(String rawRoles) {
        if (rawRoles == null || rawRoles.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawRoles.split(",")).map(String::trim).filter(r -> !r.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<SimpleGrantedAuthority> authoritiesOf(List<String> roles) {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).collect(Collectors.toList());
    }

    private void writeUnauthenticated(HttpServletResponse response, String correlationId) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .code(ErrorCode.UNAUTHENTICATED.name())
                .message("Request không đi qua Gateway hoặc thiếu xác thực người dùng")
                .status(HttpStatus.UNAUTHORIZED.value())
                .traceId(correlationId)
                .build();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
