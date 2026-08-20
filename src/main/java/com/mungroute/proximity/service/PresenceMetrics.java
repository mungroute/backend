package com.mungroute.proximity.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class PresenceMetrics {

    public enum Stage {
        SESSION_VALIDATION("session_validation"),
        REDIS_LOCATION_UPDATE("redis_location_update"),
        REDIS_GEO_SEARCH("redis_geo_search"),
        CLASSIFICATION("classification"),
        REDIS_DISTANCE_HISTORY("redis_distance_history"),
        REDIS_SESSION_SYNC("redis_session_sync"),
        DB_END_EVENTS("db_end_events"),
        DB_NOTIFICATION("db_notification");

        private final String tag;

        Stage(String tag) {
            this.tag = tag;
        }
    }

    private final Map<Stage, Timer> timers = new EnumMap<>(Stage.class);

    public PresenceMetrics(MeterRegistry meterRegistry) {
        for (Stage stage : Stage.values()) {
            timers.put(stage, Timer.builder("mungroute.presence.stage")
                    .description("Presence update processing duration by bounded stage")
                    .tag("stage", stage.tag)
                    .register(meterRegistry));
        }
    }

    public <T> T record(Stage stage, Supplier<T> action) {
        return timers.get(stage).record(action);
    }

    public void record(Stage stage, Runnable action) {
        timers.get(stage).record(action);
    }
}
