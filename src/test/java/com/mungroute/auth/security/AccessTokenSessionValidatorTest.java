package com.mungroute.auth.security;

import com.mungroute.auth.domain.RefreshToken;
import com.mungroute.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenSessionValidatorTest {
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final AccessTokenSessionValidator validator = new AccessTokenSessionValidator(repository);

    @Test
    void acceptsOnlyAnExistingUsableRefreshSession() {
        RefreshToken active = mock(RefreshToken.class);
        when(active.isUsableAt(org.mockito.ArgumentMatchers.any(OffsetDateTime.class))).thenReturn(true);
        when(repository.findByTokenHash("active")).thenReturn(Optional.of(active));

        assertThat(validator.isActive("active")).isTrue();
        assertThat(validator.isActive("missing")).isFalse();
        assertThat(validator.isActive(null)).isFalse();
    }

    @Test
    void rejectsARevokedOrExpiredRefreshSession() {
        RefreshToken revoked = mock(RefreshToken.class);
        when(revoked.isUsableAt(org.mockito.ArgumentMatchers.any(OffsetDateTime.class))).thenReturn(false);
        when(repository.findByTokenHash("revoked")).thenReturn(Optional.of(revoked));

        assertThat(validator.isActive("revoked")).isFalse();
    }
}
