package com.rental.propertiesservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Rebuilds the security context from the Gateway identity headers (spec 6.2) using the shared
 * {@link HeaderAuthenticationFilter} contract. Requests without identity headers stay anonymous so
 * public reads can be served; the security chains decide whether authentication là bắt buộc, and
 * their entry point returns the shared {@code ErrorResponse} format.
 */
public class PropertyHeaderAuthenticationFilter extends HeaderAuthenticationFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean isSkipped(String requestUri) {
        return requestUri != null && (requestUri.equals("/internal") || requestUri.startsWith("/internal/")
                || requestUri.equals("/actuator") || requestUri.startsWith("/actuator/")
                || requestUri.startsWith("/v3/api-docs") || requestUri.startsWith("/swagger-ui"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(Headers.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_CORRELATION_ID, correlationId);
        try {
            if (isSkipped(request.getRequestURI())) {
                chain.doFilter(request, response);
                return;
            }
            String userId = request.getHeader(Headers.X_USER_ID);
            String userType = request.getHeader(Headers.X_USER_TYPE);
            if (userId == null || userId.isBlank() || userType == null || userType.isBlank()) {
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }
            List<String> roles = rolesOf(request.getHeader(Headers.X_USER_ROLES));
            HeaderUser principal = new HeaderUser(userId.trim(), userType.trim(), roles);
            MDC.put(MDC_USER_ID, principal.getUserId());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    private static List<String> rolesOf(String rawRoles) {
        if (rawRoles == null || rawRoles.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawRoles.split(",")).map(String::trim).filter(r -> !r.isEmpty())
                .collect(Collectors.toList());
    }
}
