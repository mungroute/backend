package com.mungroute.weather.domain;

import java.time.Instant;

public record AsosObservation(
        Instant observedAt,
        int stationId,
        double airTemperatureC,
        double windSpeedMps,
        double solarRadiationWm2,
        double precipitationMm,
        double precipitationIntensityMmh,
        double humidityPct
) {
    public AsosObservation {
        if (observedAt == null) throw new IllegalArgumentException("관측 시각은 필수입니다.");
        if (stationId <= 0) throw new IllegalArgumentException("관측소 ID가 올바르지 않습니다.");
        if (!Double.isFinite(airTemperatureC)
                || !Double.isFinite(windSpeedMps)
                || !Double.isFinite(solarRadiationWm2)) {
            throw new IllegalArgumentException("필수 기상 관측값이 올바르지 않습니다.");
        }
    }
}
