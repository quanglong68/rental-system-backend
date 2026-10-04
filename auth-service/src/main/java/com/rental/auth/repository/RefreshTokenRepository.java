package com.rental.auth.repository;

import com.rental.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByAccountId(Long accountId);

    /** Thu hoi moi refresh token chua bi thu hoi cua 1 tai khoan (Task C5). */
    @Modifying
    @Query("""
            update RefreshToken t
            set t.revokedAt = :revokedAt
            where t.accountId = :accountId and t.revokedAt is null
            """)
    int revokeAllByAccountId(@Param("accountId") Long accountId, @Param("revokedAt") Instant revokedAt);
}
