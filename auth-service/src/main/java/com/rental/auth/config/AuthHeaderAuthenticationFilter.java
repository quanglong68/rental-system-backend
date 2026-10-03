package com.rental.auth.config;

import com.rental.common.security.HeaderAuthenticationFilter;
import java.util.Arrays;
import org.springframework.util.AntPathMatcher;

/**
 * HeaderAuthenticationFilter + bo qua cac duong PUBLIC (register/login/refresh, swagger).
 * Bo qua filter KHONG co nghia cho phep: quyet dinh cho phep van do permitAll trong
 * SecurityConfig; filter chi khong doi header o cac duong von khong co header.
 */
public class AuthHeaderAuthenticationFilter extends HeaderAuthenticationFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    protected boolean isSkipped(String requestUri) {
        if (super.isSkipped(requestUri) || requestUri == null) {
            return true;
        }
        return Arrays.stream(PublicPaths.POST).anyMatch(p -> matcher.match(p, requestUri))
                || Arrays.stream(PublicPaths.GET).anyMatch(p -> matcher.match(p, requestUri));
    }
}
