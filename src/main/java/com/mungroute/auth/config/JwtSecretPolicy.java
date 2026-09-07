package com.mungroute.auth.config;

import java.nio.charset.StandardCharsets;

final class JwtSecretPolicy {
    static final String LOCAL_DEVELOPMENT_SECRET = "local-development-secret-change-me-1234567890";

    private JwtSecretPolicy() {
    }

    static byte[] validatedBytes(String secret, boolean production) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured");
        }
        if (production && LOCAL_DEVELOPMENT_SECRET.equals(secret)) {
            throw new IllegalStateException("Production must not use the local JWT secret");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        return bytes;
    }
}
