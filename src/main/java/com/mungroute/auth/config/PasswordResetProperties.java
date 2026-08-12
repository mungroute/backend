package com.mungroute.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.password-reset")
public record PasswordResetProperties(
        Duration verificationTtl,
        Duration resetTokenTtl,
        boolean exposeCode
) {
}
