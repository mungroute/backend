package com.mungroute.weather.client;

import com.mungroute.weather.domain.AsosObservation;

import java.time.Instant;

public interface AsosWeatherGateway {
    AsosObservation fetchLatest();

    default AsosObservation fetchAt(Instant requestedAt) {
        throw new WeatherClientException("시간별 ASOS 관측 조회를 지원하지 않습니다.");
    }

    boolean configured();
}
