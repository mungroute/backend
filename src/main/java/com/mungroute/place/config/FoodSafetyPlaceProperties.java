package com.mungroute.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "external.food-safety.pet-restaurants")
public record FoodSafetyPlaceProperties(
        String exportUrl,
        Duration cacheTtl
) {
    public FoodSafetyPlaceProperties {
        exportUrl = valueOrDefault(exportUrl,
                "https://www.foodsafetykorea.go.kr/portal/petKorea/downloadExcel.do");
        cacheTtl = cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                ? Duration.ofHours(6) : cacheTtl;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
