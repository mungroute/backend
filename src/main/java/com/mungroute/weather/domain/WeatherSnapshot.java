package com.mungroute.weather.domain;

import java.time.Instant;

public record WeatherSnapshot(
        Instant observedAt,
        Instant fetchedAt,
        int stationId,
        double airTemperatureC,
        double windSpeedMps,
        double solarRadiationWm2,
        double precipitationMm,
        double precipitationIntensityMmh,
        double humidityPct,
        String source
) {
    public boolean wet() {
        return precipitationMm > 0.0 || precipitationIntensityMmh > 0.0;
    }
}
