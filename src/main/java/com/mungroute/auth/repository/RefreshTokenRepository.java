package com.mungroute.auth.repository;

import com.mungroute.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshToken token
            SET token.revokedAt = :revokedAt
            WHERE token.user.userId = :userId
              AND token.revokedAt IS NULL
            """)
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") OffsetDateTime revokedAt);
}
