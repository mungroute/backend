package com.mungroute.weather.service;

import com.mungroute.weather.client.AsosWeatherGateway;
import com.mungroute.weather.config.KmaAsosProperties;
import com.mungroute.weather.domain.AsosObservation;
import com.mungroute.weather.domain.WeatherSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class LiveWeatherService {
    private final AsosWeatherGateway gateway;
    private final KmaAsosProperties properties;
    private final Clock clock;

    private CachedObservation cached;
    private Instant lastAttemptAt;

    @Autowired
    public LiveWeatherService(AsosWeatherGateway gateway, KmaAsosProperties properties) {
        this(gateway, properties, Clock.systemUTC());
    }

    LiveWeatherService(AsosWeatherGateway gateway, KmaAsosProperties properties, Clock clock) {
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<WeatherSnapshot> resolve(Instant requestedAt) {
        Instant now = clock.instant();
        Instant effectiveRequestedAt = requestedAt == null ? now : requestedAt;
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

    private record CachedObservation(AsosObservation observation, Instant fetchedAt) {
    }
}
