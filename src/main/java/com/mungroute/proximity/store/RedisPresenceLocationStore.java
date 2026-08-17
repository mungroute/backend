package com.mungroute.proximity.store;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RedisPresenceLocationStore implements PresenceLocationStore {

    static final String GEO_KEY = "presence:geo";
    static final String SESSION_KEY_PREFIX = "presence:session:";
    static final String HISTORY_KEY_PREFIX = "presence:history:";
    static final String EXPIRY_KEY = "presence:expiry";
    private static final Duration LOCATION_TTL = Duration.ofSeconds(30);
    private static final Duration HISTORY_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final AtomicLong nextCleanupAtMillis = new AtomicLong();

    public RedisPresenceLocationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void update(PresenceLocation location) {
        cleanupExpiredMembers();
        String member = Long.toString(location.sessionId());
        String key = SESSION_KEY_PREFIX + member;
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", Long.toString(location.userId()));
        metadata.put("mode", location.mode());
        metadata.put("accuracy", Double.toString(location.accuracyMeters()));
        metadata.put("stationary", Boolean.toString(location.stationary()));
        metadata.put("updatedAt", location.updatedAt().toString());
        if (location.headingDegrees() != null) {
            metadata.put("heading", Double.toString(location.headingDegrees()));
        }

        redisTemplate.opsForGeo().add(
                GEO_KEY,
                new Point(location.longitude(), location.latitude()),
                member
        );
        redisTemplate.opsForHash().putAll(key, metadata);
        redisTemplate.expire(key, LOCATION_TTL);
        redisTemplate.opsForZSet().add(
                EXPIRY_KEY,
                member,
                System.currentTimeMillis() + LOCATION_TTL.toMillis()
        );
    }

    @Override
    public Optional<PresenceSessionState> findSession(long sessionId) {
        Map<Object, Object> metadata = redisTemplate.opsForHash()
                .entries(SESSION_KEY_PREFIX + sessionId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PresenceSessionState(
                    sessionId,
                    Long.parseLong(metadata.get("userId").toString()),
                    metadata.get("mode").toString()
            ));
        } catch (RuntimeException exception) {
            delete(sessionId);
            return Optional.empty();
        }
    }

    @Override
    public List<NearbyPresenceLocation> findNearby(
            PresenceLocation origin,
            int radiusMeters,
            int candidateLimit
    ) {
        cleanupExpiredMembers();
        var args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeCoordinates()
                .sortAscending()
                .limit(candidateLimit);
        var results = redisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(origin.longitude(), origin.latitude()),
                new Distance(radiusMeters / 1000.0, Metrics.KILOMETERS),
                args
        );
        if (results == null) {
            return List.of();
        }

        List<NearbyPresenceLocation> nearby = new ArrayList<>();
        for (var result : results) {
            var geoLocation = result.getContent();
            String member = geoLocation.getName();
            if (member.equals(Long.toString(origin.sessionId())) || geoLocation.getPoint() == null) {
                continue;
            }

            Map<Object, Object> metadata = redisTemplate.opsForHash()
                    .entries(SESSION_KEY_PREFIX + member);
            if (metadata.isEmpty()) {
                // GEO members have no individual TTL. Remove entries whose metadata expired.
                redisTemplate.opsForZSet().remove(GEO_KEY, member);
                continue;
            }

            try {
                nearby.add(new NearbyPresenceLocation(
                        Long.parseLong(member),
                        Long.parseLong(metadata.get("userId").toString()),
                        metadata.get("mode").toString(),
                        geoLocation.getPoint().getX(),
                        geoLocation.getPoint().getY(),
                        Double.parseDouble(metadata.get("accuracy").toString())
                ));
            } catch (RuntimeException ignored) {
                redisTemplate.opsForZSet().remove(GEO_KEY, member);
            }
        }
        return nearby;
    }

    @Override
    public List<Double> appendDistanceHistory(
            long sessionId,
            long otherSessionId,
            double distanceMeters
    ) {
        String key = HISTORY_KEY_PREFIX + sessionId;
        String field = Long.toString(otherSessionId);
        Object stored = redisTemplate.opsForHash().get(key, field);
        List<Double> history = new ArrayList<>();
        if (stored != null && !stored.toString().isBlank()) {
            for (String value : stored.toString().split(",")) {
                try {
                    history.add(Double.parseDouble(value));
                } catch (NumberFormatException ignored) {
                    history.clear();
                    break;
                }
            }
        }
        history.add(distanceMeters);
        if (history.size() > 3) {
            history = new ArrayList<>(history.subList(history.size() - 3, history.size()));
        }
        redisTemplate.opsForHash().put(
                key,
                field,
                history.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("")
        );
        redisTemplate.expire(key, HISTORY_TTL);
        return List.copyOf(history);
    }

    @Override
    public void delete(long sessionId) {
        String member = Long.toString(sessionId);

        redisTemplate.opsForZSet().remove(GEO_KEY, member);
        redisTemplate.opsForZSet().remove(EXPIRY_KEY, member);
        redisTemplate.delete(List.of(
                SESSION_KEY_PREFIX + member,
                HISTORY_KEY_PREFIX + member
        ));
    }

    private void cleanupExpiredMembers() {
        long now = System.currentTimeMillis();
        long scheduled = nextCleanupAtMillis.get();
        if (now < scheduled || !nextCleanupAtMillis.compareAndSet(scheduled, now + 1_000)) {
            return;
        }

        Set<String> expired = redisTemplate.opsForZSet()
                .rangeByScore(EXPIRY_KEY, 0, now, 0, 1_000);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        redisTemplate.opsForZSet().remove(GEO_KEY, expired.toArray());
        redisTemplate.opsForZSet().remove(EXPIRY_KEY, expired.toArray());
        redisTemplate.delete(expired.stream().map(member -> SESSION_KEY_PREFIX + member).toList());
    }
}
