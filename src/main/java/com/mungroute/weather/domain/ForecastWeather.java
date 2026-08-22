package com.mungroute.weather.domain;

import java.time.Instant;

public record ForecastWeather(
        Instant validAt,
        double airTemperatureC,
        double windSpeedMps,
        double solarRadiationWm2,
        double precipitationMm,
        double humidityPct
) {
    public ForecastWeather {
        if (validAt == null) throw new IllegalArgumentException("예보 시각은 필수입니다.");
        if (!Double.isFinite(airTemperatureC)
                || !Double.isFinite(windSpeedMps)
                || !Double.isFinite(solarRadiationWm2)) {
            throw new IllegalArgumentException("필수 기상 예보값이 올바르지 않습니다.");
        }
    }
}
