package com.rental.auth.config;

/**
 * Duong PUBLIC dung chung cho SecurityConfig (permitAll) va AuthHeaderAuthenticationFilter
 * (bo qua kiem tra header). Dang ky/dang nhap/refresh khong co header nguoi dung theo dinh nghia
 * (client goi thang, Gateway whitelist cho qua - docs muc 4.2, Table 7).
 */
final class PublicPaths {

    private PublicPaths() {
    }

    /** POST khong can token (docs muc 4.2 whitelist). */
    static final String[] POST = {"/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"};

    /** GET khong can token: health + swagger dev. */
    static final String[] GET = {"/actuator/health", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**",
            "/swagger-ui.html"};
}
