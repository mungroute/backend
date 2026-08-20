package com.mungroute.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.kto.pet-tour")
public record KtoPetTourProperties(
        String baseUrl,
        String serviceKey,
        String mobileOs,
        String mobileApp
) {
    public KtoPetTourProperties {
        baseUrl = valueOrDefault(baseUrl, "https://apis.data.go.kr/B551011/KorPetTourService2");
        serviceKey = serviceKey == null ? "" : serviceKey.trim();
        mobileOs = valueOrDefault(mobileOs, "ETC");
        mobileApp = valueOrDefault(mobileApp, "MungRoute");
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
