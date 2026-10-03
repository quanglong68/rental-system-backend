package com.rental.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

    private final RefreshTokenService service = new RefreshTokenService();

    @Test
    void sinhToken_docNhatVaDungDai() {
        String first = service.generateRawToken();
        String second = service.generateRawToken();

        assertThat(first).isNotEqualTo(second);
        // 32 byte -> Base64 URL-safe khong padding = 43 ky tu
        assertThat(first).hasSize(43);
    }

    @Test
    void hashVaKhop_dungVaSai() {
        String raw = service.generateRawToken();
        String stored = service.hash(raw);

        assertThat(stored).hasSize(64);
        assertThat(service.matches(raw, stored)).isTrue();
        assertThat(service.matches(raw + "x", stored)).isFalse();
        assertThat(service.matches(null, stored)).isFalse();
        assertThat(service.matches(raw, null)).isFalse();
    }

    @Test
    void han7Ngay() {
        Instant now = Instant.now();

        assertThat(service.newExpiry(now)).isEqualTo(now.plusSeconds(7 * 24 * 3600));
    }
}
