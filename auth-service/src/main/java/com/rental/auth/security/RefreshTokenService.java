package com.rental.auth.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Sinh va kiem tra refresh token (docs muc 7.1): chuoi ngau nhien 32 byte (Base64 URL-safe),
 * song 7 ngay; DB chi luu hash SHA-256, so sanh constant-time chong timing attack.
 * Rotation (cap moi + thu hoi cu) nam o Task C5.
 */
@Component
public class RefreshTokenService {

    /** Han refresh token (docs muc 7.1: 7 ngay). */
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final SecureRandom random = new SecureRandom();

    /** Sinh refresh token tho de tra cho client (khong luu tho vao DB). */
    public String generateRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hash SHA-256 (hex) de luu DB. */
    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Thieu SHA-256", impossible);
        }
    }

    /** So sanh constant-time giua token client gui va hash trong DB. */
    public boolean matches(String rawToken, String storedHash) {
        if (rawToken == null || storedHash == null) {
            return false;
        }
        byte[] a = hash(rawToken).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    public Instant newExpiry(Instant now) {
        return now.plus(REFRESH_TOKEN_TTL);
    }
}
