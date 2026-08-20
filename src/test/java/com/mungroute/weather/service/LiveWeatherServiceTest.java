package com.mungroute.weather.service;

import com.mungroute.weather.client.AsosWeatherGateway;
import com.mungroute.weather.config.KmaAsosProperties;
import com.mungroute.weather.domain.AsosObservation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LiveWeatherServiceTest {

    @Test
    void returnsNowcastThenFallsBackToTheLastValidCache() {
        Instant observedAt = Instant.parse("2026-08-20T06:00:00Z");
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T06:20:00Z"));
        AtomicInteger calls = new AtomicInteger();
        AsosWeatherGateway gateway = new AsosWeatherGateway() {
            @Override
            public AsosObservation fetchLatest() {
                if (calls.getAndIncrement() > 0) throw new IllegalStateException("temporary failure");
                return observation(observedAt);
            }

            @Override
            public boolean configured() {
                return true;
            }
        };
        LiveWeatherService service = new LiveWeatherService(gateway, properties(), clock);

        assertThat(service.resolve(clock.instant())).get().extracting("source").isEqualTo("NOWCAST");
        clock.advance(Duration.ofMinutes(11));
        assertThat(service.resolve(clock.instant())).get().extracting("source").isEqualTo("CACHED");
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotUseNowcastForAnArbitraryScenarioTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T06:20:00Z"));
        AsosWeatherGateway gateway = new AsosWeatherGateway() {
            @Override
            public AsosObservation fetchLatest() {
                throw new AssertionError("과거 시나리오 요청은 API를 호출하면 안 됩니다.");
            }

            @Override
            public boolean configured() {
                return true;
            }
        };
        LiveWeatherService service = new LiveWeatherService(gateway, properties(), clock);

        assertThat(service.resolve(Instant.parse("2026-08-11T06:00:00Z"))).isEmpty();
    }

    private static AsosObservation observation(Instant observedAt) {
        return new AsosObservation(observedAt, 108, 31.3, 2.8, 855.56, 0, 0, 48);
    }

    private static KmaAsosProperties properties() {
        return new KmaAsosProperties(
                "https://example.test", "test-key", 108,
                Duration.ofMinutes(10), Duration.ofHours(2),
                Duration.ofMinutes(1), Duration.ofMinutes(90)
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
