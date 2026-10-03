package com.rental.auth.security;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ky va kiem tra access token JWT HS256 (docs muc 7.1).
 * Claims: sub (account id), typ, roles, iss = rental-auth, iat, exp (+15 phut), jti.
 * Secret toi thieu 32 ky tu, doc tu env JWT_SECRET; ngan hon thi fail-fast khi start.
 * Het han -&gt; 401 TOKEN_EXPIRED; sai chu ky/sai dinh dang -&gt; 401 UNAUTHENTICATED.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final String issuer;
    private final long accessExpirationSeconds;

    public JwtProvider(@Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer:rental-auth}") String issuer,
            @Value("${jwt.access-expiration:900}") long accessExpirationSeconds) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("jwt.secret phai toi thieu 32 ky tu (HS256 can khoa 256-bit)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = issuer;
        this.accessExpirationSeconds = accessExpirationSeconds;
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public String generateAccessToken(Long accountId, String userType, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationSeconds * 1000);
        return Jwts.builder().subject(String.valueOf(accountId)).claim("typ", userType).claim("roles", roles)
                .issuer(issuer).issuedAt(now).expiration(expiry).id(UUID.randomUUID().toString()).signWith(key)
                .compact();
    }

    public JwtClaims parseAndValidate(String token) {
        Claims payload;
        try {
            payload = Jwts.parser().verifyWith(key).requireIssuer(issuer).clockSkewSeconds(30).build()
                    .parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException expired) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Token da het han");
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Token khong hop le");
        }
        List<String> roles = payload.get("roles", List.class);
        return new JwtClaims(Long.valueOf(payload.getSubject()), payload.get("typ", String.class),
                roles != null ? List.copyOf(roles) : List.of(), payload.getId());
    }
}
