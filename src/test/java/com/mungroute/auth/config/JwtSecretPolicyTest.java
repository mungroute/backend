package com.mungroute.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretPolicyTest {
    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> JwtSecretPolicy.validatedBytes(null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> JwtSecretPolicy.validatedBytes("too-short", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void rejectsLocalDefaultInProduction() {
        assertThatThrownBy(() -> JwtSecretPolicy.validatedBytes(
                JwtSecretPolicy.LOCAL_DEVELOPMENT_SECRET,
                true
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local JWT secret");
    }

    @Test
    void permitsLocalDefaultOutsideProduction() {
        assertThat(JwtSecretPolicy.validatedBytes(
                JwtSecretPolicy.LOCAL_DEVELOPMENT_SECRET,
                false
        )).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void permitsStrongProductionSecret() {
        assertThat(JwtSecretPolicy.validatedBytes(
                "production-secret-with-at-least-thirty-two-bytes",
                true
        )).hasSizeGreaterThanOrEqualTo(32);
    }
}
