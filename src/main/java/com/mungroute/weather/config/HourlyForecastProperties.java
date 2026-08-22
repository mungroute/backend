package com.mungroute.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.weather.forecast")
public record HourlyForecastProperties(
        String baseUrl,
        double latitude,
        double longitude
) {
    public HourlyForecastProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.open-meteo.com" : baseUrl.trim();
        latitude = latitude == 0.0 ? 37.5665 : latitude;
        longitude = longitude == 0.0 ? 126.9780 : longitude;
    }
}
