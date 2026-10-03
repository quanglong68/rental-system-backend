package com.rental.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rental.common.error.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "test-only-secret-at-least-32-chars-long-00";
    private static final String OTHER_SECRET = "another-test-secret-32-chars-minimum-00";

    private final JwtProvider provider = new JwtProvider(SECRET, "rental-auth", 900);

    @Test
    void kyVaParse_dungClaims() {
        String token = provider.generateAccessToken(1024L, "STAFF", List.of("QUAN_LY", "SALE"));

        JwtClaims claims = provider.parseAndValidate(token);

        assertThat(claims.accountId()).isEqualTo(1024L);
        assertThat(claims.userType()).isEqualTo("STAFF");
        assertThat(claims.roles()).containsExactlyInAnyOrder("QUAN_LY", "SALE");
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(provider.getAccessExpirationSeconds()).isEqualTo(900);
    }

    @Test
    void tokenHetHan_thanhTokenExpired() {
        String expired = Jwts.builder().subject("7").claim("typ", "CUSTOMER").claim("roles", List.of("CUSTOMER"))
                .issuer("rental-auth").issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000)).id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();

        assertThatThrownBy(() -> provider.parseAndValidate(expired)).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TOKEN_EXPIRED");
    }

    @Test
    void saiChuKy_thanhUnauthenticated() {
        String forged = Jwts.builder().subject("7").claim("typ", "ADMIN").claim("roles", List.of("ADMIN"))
                .issuer("rental-auth").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8))).compact();

        assertThatThrownBy(() -> provider.parseAndValidate(forged)).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "UNAUTHENTICATED");
    }

    @Test
    void tokenRac_thanhUnauthenticated() {
        assertThatThrownBy(() -> provider.parseAndValidate("not-a-jwt")).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "UNAUTHENTICATED");
    }

    @Test
    void secretNgan_failFast() {
        assertThatThrownBy(() -> new JwtProvider("short", "rental-auth", 900)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
