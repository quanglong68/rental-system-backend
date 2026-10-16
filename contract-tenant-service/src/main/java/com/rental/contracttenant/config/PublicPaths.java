package com.rental.contracttenant.config;

/**
 * Public operational and documentation paths. Gateway remains responsible for external JWT validation.
 */
final class PublicPaths {

    private PublicPaths() {
    }

    static final String[] GET = {"/actuator/health", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**",
            "/swagger-ui.html"};
}
