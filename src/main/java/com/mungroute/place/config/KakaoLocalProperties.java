package com.mungroute.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.kakao.local")
public record KakaoLocalProperties(
        String baseUrl,
        String restApiKey
) {
    public KakaoLocalProperties {
        baseUrl = valueOrDefault(baseUrl, "https://dapi.kakao.com");
        restApiKey = restApiKey == null ? "" : restApiKey.trim();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
