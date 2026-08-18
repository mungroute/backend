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
import java.util.UUID;

@Component
public class RedisPresenceLocationStore implements PresenceLocationStore {

    static final String GEO_KEY = "presence:geo";
    static final String SESSION_KEY_PREFIX = "presence:session:";
    static final String HISTORY_KEY_PREFIX = "presence:history:";
    static final String NEARBY_KEY_PREFIX = "presence:nearby:";
    static final String EXPIRY_KEY = "presence:expiry";
    static final String MEET_CANDIDATE_KEY_PREFIX = "meet:candidate:";
    static final String MEET_CANDIDATE_PAIR_KEY_PREFIX = "meet:candidate-pair:";
    private static final Duration LOCATION_TTL = Duration.ofSeconds(30);
    private static final Duration HISTORY_TTL = Duration.ofSeconds(60);
    private static final Duration NEARBY_TTL = Duration.ofSeconds(30);

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
    public NearbyPresenceTransition synchronizeNearbySessions(
            long sessionId,
            List<Long> nearbySessionIds
    ) {
        String key = NEARBY_KEY_PREFIX + sessionId;
        Set<String> stored = redisTemplate.opsForSet().members(key);
        Set<String> previous = stored == null ? Set.of() : Set.copyOf(stored);
        Set<String> current = nearbySessionIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<Long> entered = current.stream()
                .filter(value -> !previous.contains(value))
                .map(Long::valueOf)
                .sorted()
                .toList();
        List<Long> left = previous.stream()
                .filter(value -> !current.contains(value))
                .map(Long::valueOf)
                .sorted()
                .toList();

        if (!previous.equals(current)) {
            redisTemplate.delete(key);
            if (!current.isEmpty()) {
                redisTemplate.opsForSet().add(key, current.toArray(String[]::new));
            }
        }
        if (!current.isEmpty()) {
            redisTemplate.expire(key, NEARBY_TTL);
        }
        return new NearbyPresenceTransition(entered, left);
    }

    @Override
    public String issueMeetCandidateRef(long viewerSessionId, long targetSessionId, long targetUserId) {
        String pairKey = MEET_CANDIDATE_PAIR_KEY_PREFIX + viewerSessionId + ":" + targetSessionId;
        String existing = redisTemplate.opsForValue().get(pairKey);
        if (existing != null) {
            return existing;
        }
        String candidateRef = UUID.randomUUID().toString();
        redisTemplate.opsForHash().putAll(MEET_CANDIDATE_KEY_PREFIX + candidateRef, Map.of(
                "viewerSessionId", Long.toString(viewerSessionId),
                "targetSessionId", Long.toString(targetSessionId),
                "targetUserId", Long.toString(targetUserId)
        ));
        redisTemplate.expire(MEET_CANDIDATE_KEY_PREFIX + candidateRef, LOCATION_TTL);
        redisTemplate.opsForValue().set(pairKey, candidateRef, LOCATION_TTL);
        return candidateRef;
    }

    @Override
    public Optional<MeetCandidateTarget> resolveMeetCandidateRef(long viewerSessionId, String candidateRef) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(MEET_CANDIDATE_KEY_PREFIX + candidateRef);
        if (values.isEmpty()) return Optional.empty();
        try {
            if (Long.parseLong(values.get("viewerSessionId").toString()) != viewerSessionId) {
                return Optional.empty();
            }
            return Optional.of(new MeetCandidateTarget(
                    Long.parseLong(values.get("targetSessionId").toString()),
                    Long.parseLong(values.get("targetUserId").toString())
            ));
        } catch (RuntimeException exception) {
            redisTemplate.delete(MEET_CANDIDATE_KEY_PREFIX + candidateRef);
            return Optional.empty();
        }
    }

    @Override
    public Optional<PresenceLocation> findLocation(long sessionId) {
        String member = Long.toString(sessionId);
        Map<Object, Object> metadata = redisTemplate.opsForHash().entries(SESSION_KEY_PREFIX + member);
        List<Point> positions = redisTemplate.opsForGeo().position(GEO_KEY, member);
        if (metadata.isEmpty() || positions == null || positions.isEmpty() || positions.getFirst() == null) {
            return Optional.empty();
        }
        try {
            Point point = positions.getFirst();
            Object heading = metadata.get("heading");
            return Optional.of(new PresenceLocation(
                    sessionId,
                    Long.parseLong(metadata.get("userId").toString()),
                    metadata.get("mode").toString(),
                    point.getX(),
                    point.getY(),
                    Double.parseDouble(metadata.get("accuracy").toString()),
                    heading == null ? null : Double.parseDouble(heading.toString()),
                    Boolean.parseBoolean(metadata.get("stationary").toString()),
                    OffsetDateTime.parse(metadata.get("updatedAt").toString())
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(long sessionId) {
        String member = Long.toString(sessionId);

        redisTemplate.opsForZSet().remove(GEO_KEY, member);
        redisTemplate.opsForZSet().remove(EXPIRY_KEY, member);
        redisTemplate.delete(List.of(
                SESSION_KEY_PREFIX + member,
                HISTORY_KEY_PREFIX + member,
                NEARBY_KEY_PREFIX + member
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
