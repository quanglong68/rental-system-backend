package com.rental.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.ErrorResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Doc 3 header Gateway chuyen vao, tao Authentication cho request (docs muc 6.2).
 * Service khong co JWT_SECRET va khong verify JWT; filter nay la cua ngõ phan quyen.
 * Moi service dang ky filter truoc FilterSecurityInterceptor (xem javadoc cua
 * {@link #isSkipped(String)} neu can mo rong duong bo qua).
 *
 * <ul>
 *   <li>Duong {@code /internal/**} (service goi service) va {@code /actuator/**}:
 *       khong kiem quyen nguoi dung, chi giu MDC de truy vet.</li>
 *   <li>Thieu {@code X-User-Id} hoac {@code X-User-Type} (request khong qua Gateway):
 *       tra 401 {@code UNAUTHENTICATED} dung format {@link ErrorResponse}.</li>
 *   <li>Du header: principal la {@link HeaderUser}, authority {@code ROLE_<role>}.</li>
 * </ul>
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    /** Key MDC de service dua vao log pattern (log co correlationId, docs muc 2). */
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_USER_ID = "userId";

    private final ObjectMapper objectMapper;

    public HeaderAuthenticationFilter() {
        this(new ObjectMapper());
    }

    HeaderAuthenticationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = correlationIdOf(request);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        try {
            if (isSkipped(request.getRequestURI())) {
                chain.doFilter(request, response);
                return;
            }

            String userId = request.getHeader(Headers.X_USER_ID);
            String userType = request.getHeader(Headers.X_USER_TYPE);
            if (isBlank(userId) || isBlank(userType)) {
                writeUnauthenticated(response, correlationId);
                return;
            }

            List<String> roles = rolesOf(request.getHeader(Headers.X_USER_ROLES));
            HeaderUser principal = new HeaderUser(userId.trim(), userType.trim(), roles);
            MDC.put(MDC_USER_ID, principal.getUserId());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
                    null, authoritiesOf(roles));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    /**
     * Duong bo qua kiem tra header. Mac dinh theo docs muc 6.2; service co the override
     * de mo rong (vd them {@code /swagger-ui}, {@code /v3/api-docs} cho dev).
     */
    protected boolean isSkipped(String requestUri) {
        return requestUri != null && (requestUri.equals("/internal") || requestUri.startsWith("/internal/")
                || requestUri.equals("/actuator") || requestUri.startsWith("/actuator/"));
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

    private static String correlationIdOf(HttpServletRequest request) {
        String correlationId = request.getHeader(Headers.X_CORRELATION_ID);
        return correlationId != null && !correlationId.isBlank() ? correlationId : UUID.randomUUID().toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void writeUnauthenticated(HttpServletResponse response, String correlationId) throws IOException {
        ErrorResponse body = ErrorResponse.builder().code(ErrorCode.UNAUTHENTICATED.name())
                .message("Request khong di qua Gateway").status(HttpStatus.UNAUTHORIZED.value())
                .traceId(correlationId).build();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
