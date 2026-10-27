package com.rental.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Restricts the staff-building lookup shared by Property and Asset services. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class StaffAssignmentAuthFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Staff-Assignment-Key";
    private final byte[] expected;

    public StaffAssignmentAuthFilter(@Value("${staff.assignment.internal-key:}") String key) {
        expected = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.matches("/internal/staff/[^/]+/buildings");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var values = Collections.list(request.getHeaders(HEADER));
        if (expected.length < 32 || values.size() != 1
                || !MessageDigest.isEqual(expected, values.getFirst().getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
