package com.mungroute.weather.client;

import com.mungroute.weather.domain.ForecastWeather;

import java.time.Instant;

public interface ForecastWeatherGateway {
    ForecastWeather fetch(Instant requestedAt);
}
