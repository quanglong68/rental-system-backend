package com.rental.listingservice.config;

import com.rental.common.security.HeaderAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

public class ListingHeaderAuthenticationFilter extends HeaderAuthenticationFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean publicRead = HttpMethod.GET.matches(request.getMethod())
                && (matcher.match("/api/v1/listings", path) || matcher.match("/api/v1/listings/*", path));
        if (publicRead) {
            chain.doFilter(request, response);
            return;
        }
        super.doFilterInternal(request, response, chain);
    }
}
