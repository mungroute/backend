package com.mungroute.auth.security;

import com.mungroute.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** Makes an access JWT invalid as soon as its refresh session is revoked. */
@Component
public class AccessTokenSessionValidator {
    private final RefreshTokenRepository refreshTokenRepository;

    public AccessTokenSessionValidator(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public boolean isActive(String sessionHash) {
        if (sessionHash == null || sessionHash.isBlank()) return false;
        OffsetDateTime now = OffsetDateTime.now();
        return refreshTokenRepository.findByTokenHash(sessionHash)
                .filter(token -> token.isUsableAt(now))
                .isPresent();
    }
}
