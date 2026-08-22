package com.mungroute.weather.service;

import com.mungroute.weather.client.AsosWeatherGateway;
import com.mungroute.weather.client.ForecastWeatherGateway;
import com.mungroute.weather.config.KmaAsosProperties;
import com.mungroute.weather.domain.AsosObservation;
import com.mungroute.weather.domain.WeatherSnapshot;
import com.mungroute.weather.domain.ForecastWeather;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@Service
public class LiveWeatherService {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final java.util.Set<LocalTime> REFERENCE_TIMES = java.util.Set.of(
            LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(18, 0)
    );

    private final AsosWeatherGateway gateway;
    private final ForecastWeatherGateway forecastGateway;
    private final KmaAsosProperties properties;
    private final Clock clock;

    private CachedObservation cached;
    private final Map<Instant, CachedObservation> hourlyObservations = new HashMap<>();
    private final Map<Instant, CachedForecast> hourlyForecasts = new HashMap<>();
    private Instant lastAttemptAt;

    @Autowired
    public LiveWeatherService(
            AsosWeatherGateway gateway,
            ForecastWeatherGateway forecastGateway,
            KmaAsosProperties properties
    ) {
        this(gateway, forecastGateway, properties, Clock.systemUTC());
    }

    LiveWeatherService(AsosWeatherGateway gateway, KmaAsosProperties properties, Clock clock) {
        this(gateway, requestedAt -> {
            throw new IllegalStateException("예보 게이트웨이가 설정되지 않았습니다.");
        }, properties, clock);
    }

    LiveWeatherService(
            AsosWeatherGateway gateway,
            ForecastWeatherGateway forecastGateway,
            KmaAsosProperties properties,
            Clock clock
    ) {
        this.gateway = gateway;
        this.forecastGateway = forecastGateway;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<WeatherSnapshot> resolve(Instant requestedAt) {
        Instant now = clock.instant();
        Instant effectiveRequestedAt = requestedAt == null ? now : requestedAt;
        if (isTodayReferenceTime(effectiveRequestedAt, now)) {
            return effectiveRequestedAt.isAfter(now)
                    ? resolveForecast(effectiveRequestedAt, now)
                    : resolveHourlyObservation(effectiveRequestedAt, now);
        }
        if (absoluteDuration(effectiveRequestedAt, now).compareTo(properties.liveRequestWindow()) > 0
                || !gateway.configured()) {
            return Optional.empty();
        }
        synchronized (this) {
            if (cached != null && Duration.between(cached.fetchedAt(), now).compareTo(properties.cacheTtl()) < 0) {
                return Optional.of(snapshot(cached, "NOWCAST"));
            }
            boolean retryAllowed = lastAttemptAt == null
                    || Duration.between(lastAttemptAt, now).compareTo(properties.retryDelay()) >= 0;
            if (retryAllowed) {
                lastAttemptAt = now;
                try {
                    AsosObservation observation = gateway.fetchLatest();
                    if (freshEnough(observation.observedAt(), now)) {
                        cached = new CachedObservation(observation, now);
                        return Optional.of(snapshot(cached, "NOWCAST"));
                    }
                } catch (RuntimeException ignored) {
                    // 최신 정상 캐시가 있으면 아래에서 CACHED로 제공한다.
                }
            }
            if (cached != null && freshEnough(cached.observation().observedAt(), now)) {
                return Optional.of(snapshot(cached, "CACHED"));
            }
            return Optional.empty();
        }
    }

    private Optional<WeatherSnapshot> resolveHourlyObservation(Instant requestedAt, Instant now) {
        if (!gateway.configured()) return resolveForecast(requestedAt, now);
        Instant key = requestedAt.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        synchronized (this) {
            CachedObservation existing = hourlyObservations.get(key);
            if (existing != null && Duration.between(existing.fetchedAt(), now).compareTo(properties.cacheTtl()) < 0) {
                return Optional.of(snapshot(existing, "OBSERVED"));
            }
            try {
                AsosObservation observation = gateway.fetchAt(requestedAt);
                CachedObservation fetched = new CachedObservation(observation, now);
                hourlyObservations.put(key, fetched);
                return Optional.of(snapshot(fetched, "OBSERVED"));
            } catch (RuntimeException ignored) {
                return resolveForecast(requestedAt, now);
            }
        }
    }

    private Optional<WeatherSnapshot> resolveForecast(Instant requestedAt, Instant now) {
        Instant key = requestedAt.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        synchronized (this) {
            CachedForecast existing = hourlyForecasts.get(key);
            if (existing != null && Duration.between(existing.fetchedAt(), now).compareTo(properties.cacheTtl()) < 0) {
                return Optional.of(snapshot(existing, "FORECAST"));
            }
            try {
                ForecastWeather forecast = forecastGateway.fetch(requestedAt);
                CachedForecast fetched = new CachedForecast(forecast, now);
                hourlyForecasts.put(key, fetched);
                return Optional.of(snapshot(fetched, "FORECAST"));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
    }

    private static boolean isTodayReferenceTime(Instant requestedAt, Instant now) {
        var requestedLocal = requestedAt.atZone(SERVICE_ZONE);
        var nowLocal = now.atZone(SERVICE_ZONE);
        return requestedLocal.toLocalDate().equals(nowLocal.toLocalDate())
                && requestedLocal.getSecond() == 0
                && requestedLocal.getNano() == 0
                && REFERENCE_TIMES.contains(requestedLocal.toLocalTime());
    }

    private boolean freshEnough(Instant observedAt, Instant now) {
        Duration age = Duration.between(observedAt, now);
        return !age.isNegative() && age.compareTo(properties.staleTtl()) <= 0;
    }

    private static Duration absoluteDuration(Instant left, Instant right) {
        Duration duration = Duration.between(left, right);
        return duration.isNegative() ? duration.negated() : duration;
    }

    private static WeatherSnapshot snapshot(CachedObservation cached, String source) {
        AsosObservation observation = cached.observation();
        return new WeatherSnapshot(
                observation.observedAt(), cached.fetchedAt(), observation.stationId(),
                observation.airTemperatureC(), observation.windSpeedMps(), observation.solarRadiationWm2(),
                observation.precipitationMm(), observation.precipitationIntensityMmh(),
                observation.humidityPct(), source
        );
    }

    private static WeatherSnapshot snapshot(CachedForecast cached, String source) {
        ForecastWeather forecast = cached.forecast();
        return new WeatherSnapshot(
                forecast.validAt(), cached.fetchedAt(), 108,
                forecast.airTemperatureC(), forecast.windSpeedMps(), forecast.solarRadiationWm2(),
                forecast.precipitationMm(), forecast.precipitationMm(), forecast.humidityPct(), source
        );
    }

    private record CachedObservation(AsosObservation observation, Instant fetchedAt) {
    }

    private record CachedForecast(ForecastWeather forecast, Instant fetchedAt) {
    }
}
