package com.mungroute.auth.adapter;

import com.mungroute.auth.repository.RefreshTokenRepository;
import com.mungroute.user.port.UserSessionRevocationPort;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class UserSessionRevocationAdapter implements UserSessionRevocationPort {
    private final RefreshTokenRepository refreshTokenRepository;

    public UserSessionRevocationAdapter(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public void revokeAllActive(long userId, OffsetDateTime revokedAt) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, revokedAt);
    }
}
