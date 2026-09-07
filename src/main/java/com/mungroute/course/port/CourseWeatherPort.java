package com.mungroute.course.port;

import java.time.Instant;
import java.util.Optional;

/** Current weather data required while recalculating course temperatures. */
public interface CourseWeatherPort {
    Optional<Snapshot> resolve(Instant requestedAt);

    record Snapshot(
            Instant observedAt,
            double airTemperatureC,
            double windSpeedMps,
            double solarRadiationWm2,
            String source
    ) {
    }
}
