package com.rental.contracttenant.config;

import com.rental.common.security.HeaderAuthenticationFilter;
import java.util.Arrays;
import org.springframework.util.AntPathMatcher;

/**
 * Reuses shared Gateway-header authentication while allowing public operational and documentation paths.
 */
public class ContractHeaderAuthenticationFilter extends HeaderAuthenticationFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    protected boolean isSkipped(String requestUri) {
        if (super.isSkipped(requestUri) || requestUri == null) {
            return true;
        }
        return Arrays.stream(PublicPaths.GET).anyMatch(path -> matcher.match(path, requestUri));
    }
}
