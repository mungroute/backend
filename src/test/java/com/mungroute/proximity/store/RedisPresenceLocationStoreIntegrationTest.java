package com.mungroute.proximity.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class RedisPresenceLocationStoreIntegrationTest {

    @Autowired
    PresenceLocationStore store;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void boundsCachedSessionAuthorizationToTheLocationFreshnessWindow() {
        long sessionId = ThreadLocalRandom.current().nextLong(1_000_000, 10_000_000);
        try {
            store.cacheSession(new PresenceSessionState(sessionId, 101L, "meet"));

            assertThat(store.findSession(sessionId))
                    .contains(new PresenceSessionState(sessionId, 101L, "meet"));
            assertThat(redisTemplate.getExpire(
                    RedisPresenceLocationStore.AUTH_SESSION_KEY_PREFIX + sessionId,
                    TimeUnit.SECONDS
            )).isBetween(1L, 30L);
        } finally {
            store.delete(sessionId);
        }
    }

    @Test
    void distinguishesEntryContinuousPresenceAndExit() {
        long recipientSessionId = ThreadLocalRandom.current().nextLong(10_000_000, 20_000_000);
        long otherSessionId = recipientSessionId + 1;
        try {
            var entered = store.synchronizeNearbySessions(recipientSessionId, List.of(otherSessionId));
            var continuous = store.synchronizeNearbySessions(recipientSessionId, List.of(otherSessionId));
            var left = store.synchronizeNearbySessions(recipientSessionId, List.of());

            assertThat(entered.enteredSessionIds()).containsExactly(otherSessionId);
            assertThat(entered.leftSessionIds()).isEmpty();
            assertThat(continuous.enteredSessionIds()).isEmpty();
            assertThat(continuous.leftSessionIds()).isEmpty();
            assertThat(left.enteredSessionIds()).isEmpty();
            assertThat(left.leftSessionIds()).containsExactly(otherSessionId);
        } finally {
            store.delete(recipientSessionId);
        }
    }

    @Test
    void loadsNearbyMetadataThroughRedisPipeline() {
        long originSessionId = ThreadLocalRandom.current().nextLong(20_000_000, 30_000_000);
        long nearbySessionId = originSessionId + 1;
        PresenceLocation origin = new PresenceLocation(
                originSessionId, 101L, "WALK", 126.9780, 37.5665,
                5.0, null, false, OffsetDateTime.now());
        PresenceLocation nearby = new PresenceLocation(
                nearbySessionId, 102L, "WALK", 126.9781, 37.5666,
                7.0, null, false, OffsetDateTime.now());
        try {
            store.update(origin);
            store.update(nearby);

            assertThat(store.findNearby(origin, 100, 10))
                    .anySatisfy(found -> {
                        assertThat(found.sessionId()).isEqualTo(nearbySessionId);
                        assertThat(found.userId()).isEqualTo(102L);
                        assertThat(found.mode()).isEqualTo("WALK");
                    });
        } finally {
            store.delete(originSessionId);
            store.delete(nearbySessionId);
        }
    }

    @Test
    void atomicallyKeepsTheLatestThreeDistanceSamples() {
        long sessionId = ThreadLocalRandom.current().nextLong(30_000_000, 40_000_000);
        long otherSessionId = sessionId + 1;
        try {
            store.appendDistanceHistory(sessionId, otherSessionId, 40.0);
            store.appendDistanceHistory(sessionId, otherSessionId, 35.0);
            store.appendDistanceHistory(sessionId, otherSessionId, 30.0);

            assertThat(store.appendDistanceHistory(sessionId, otherSessionId, 25.0))
                    .containsExactly(35.0, 30.0, 25.0);
        } finally {
            store.delete(sessionId);
        }
    }
}
