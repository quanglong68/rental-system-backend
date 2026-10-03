package com.rental.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthHeaderAuthenticationFilterTest extends AuthHeaderAuthenticationFilter {

    @Test
    void duongPublic_vaNoiBo_biBoQua() {
        assertThat(isSkipped("/api/v1/auth/register")).isTrue();
        assertThat(isSkipped("/api/v1/auth/login")).isTrue();
        assertThat(isSkipped("/api/v1/auth/refresh")).isTrue();
        assertThat(isSkipped("/v3/api-docs")).isTrue();
        assertThat(isSkipped("/v3/api-docs/auth-api")).isTrue();
        assertThat(isSkipped("/swagger-ui/index.html")).isTrue();
        assertThat(isSkipped("/internal/staff/1/buildings")).isTrue();
        assertThat(isSkipped("/actuator/health")).isTrue();
    }

    @Test
    void duongCanAuth_khongBiBoQua() {
        assertThat(isSkipped("/api/v1/auth/me")).isFalse();
        assertThat(isSkipped("/api/v1/auth/password")).isFalse();
        assertThat(isSkipped("/api/v1/auth/accounts")).isFalse();
    }
}
