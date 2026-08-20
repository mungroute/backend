package com.mungroute.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "external.kma.asos")
public record KmaAsosProperties(
        String baseUrl,
        String authKey,
        int stationId,
        Duration cacheTtl,
        Duration staleTtl,
        Duration retryDelay,
        Duration liveRequestWindow
) {
    public KmaAsosProperties {
        baseUrl = valueOrDefault(baseUrl, "https://apihub.kma.go.kr");
        authKey = authKey == null ? "" : authKey.trim();
        stationId = stationId <= 0 ? 108 : stationId;
        cacheTtl = positiveOrDefault(cacheTtl, Duration.ofMinutes(10));
        staleTtl = positiveOrDefault(staleTtl, Duration.ofHours(2));
        retryDelay = positiveOrDefault(retryDelay, Duration.ofMinutes(1));
        liveRequestWindow = positiveOrDefault(liveRequestWindow, Duration.ofMinutes(90));
    }

    public boolean configured() {
        return !authKey.isBlank();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
