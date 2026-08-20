package com.mungroute.proximity.store;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    private static final DefaultRedisScript<Long> UPDATE_LOCATION_SCRIPT = new DefaultRedisScript<>("""
            redis.call('GEOADD', KEYS[1], ARGV[1], ARGV[2], ARGV[3])
            redis.call('HSET', KEYS[2], ARGV[3], ARGV[4])
            redis.call('ZADD', KEYS[3], ARGV[5], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> APPEND_HISTORY_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('HGET', KEYS[1], ARGV[1])
            local values = {}
            if stored and stored ~= '' then
                for value in string.gmatch(stored, '[^,]+') do
                    table.insert(values, value)
                end
            end
            while #values >= 3 do
                table.remove(values, 1)
            end
            table.insert(values, ARGV[2])
            local updated = table.concat(values, ',')
            redis.call('HSET', KEYS[1], ARGV[1], updated)
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return updated
            """, String.class);

    static final String GEO_KEY = "presence:geo";
    static final String SESSION_KEY_PREFIX = "presence:session:";
    static final String METADATA_KEY = "presence:metadata";
    static final String AUTH_SESSION_KEY_PREFIX = "presence:auth-session:";
    static final String HISTORY_KEY_PREFIX = "presence:history:";
    static final String NEARBY_KEY_PREFIX = "presence:nearby:";
    static final String EXPIRY_KEY = "presence:expiry";
    static final String MEET_CANDIDATE_KEY_PREFIX = "meet:candidate:";
    static final String MEET_CANDIDATE_PAIR_KEY_PREFIX = "meet:candidate-pair:";
    private static final Duration LOCATION_TTL = Duration.ofSeconds(30);
    private static final Duration SESSION_CACHE_TTL = Duration.ofMinutes(30);
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
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", Long.toString(location.userId()));
        metadata.put("mode", location.mode());
        metadata.put("accuracy", Double.toString(location.accuracyMeters()));
        metadata.put("stationary", Boolean.toString(location.stationary()));
        metadata.put("updatedAt", location.updatedAt().toString());
        if (location.headingDegrees() != null) {
            metadata.put("heading", Double.toString(location.headingDegrees()));
        }

        redisTemplate.execute(
                UPDATE_LOCATION_SCRIPT,
                List.of(GEO_KEY, METADATA_KEY, EXPIRY_KEY),
                Double.toString(location.longitude()),
                Double.toString(location.latitude()),
                member,
                encodeMetadata(metadata),
                Long.toString(System.currentTimeMillis() + LOCATION_TTL.toMillis())
        );
    }

    @Override
    public Optional<PresenceSessionState> findSession(long sessionId) {
        Map<Object, Object> metadata = redisTemplate.opsForHash()
                .entries(AUTH_SESSION_KEY_PREFIX + sessionId);
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
    public void cacheSession(PresenceSessionState session) {
        String key = AUTH_SESSION_KEY_PREFIX + session.sessionId();
        redisTemplate.opsForHash().putAll(key, Map.of(
                "userId", Long.toString(session.userId()),
                "mode", session.mode()
        ));
        redisTemplate.expire(key, SESSION_CACHE_TTL);
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

        List<GeoCandidate> candidates = new ArrayList<>();
        for (var result : results) {
            var geoLocation = result.getContent();
            String member = geoLocation.getName();
            if (member.equals(Long.toString(origin.sessionId())) || geoLocation.getPoint() == null) {
                continue;
            }
            candidates.add(new GeoCandidate(member, geoLocation.getPoint()));
        }

        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Object> metadataResults = redisTemplate.opsForHash().multiGet(
                METADATA_KEY, candidates.stream().map(GeoCandidate::member).map(value -> (Object) value).toList());

        List<NearbyPresenceLocation> nearby = new ArrayList<>();
        List<String> invalidMembers = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            GeoCandidate candidate = candidates.get(index);
            Object stored = index < metadataResults.size() ? metadataResults.get(index) : null;
            if (stored == null) {
                invalidMembers.add(candidate.member());
                continue;
            }

            try {
                StoredMetadata metadata = decodeMetadata(stored.toString());
                nearby.add(new NearbyPresenceLocation(
                        Long.parseLong(candidate.member()),
                        metadata.userId(),
                        metadata.mode(),
                        candidate.point().getX(),
                        candidate.point().getY(),
                        metadata.accuracy()
                ));
            } catch (RuntimeException ignored) {
                invalidMembers.add(candidate.member());
            }
        }
        if (!invalidMembers.isEmpty()) {
            // GEO members have no individual TTL. Remove entries whose metadata expired.
            redisTemplate.opsForZSet().remove(GEO_KEY, invalidMembers.toArray());
        }
        return nearby;
    }

    private String encodeMetadata(Map<String, String> metadata) {
        return String.join("|",
                metadata.get("userId"),
                metadata.get("mode"),
                metadata.get("accuracy"),
                metadata.getOrDefault("heading", ""),
                metadata.get("stationary"),
                metadata.get("updatedAt"));
    }

    private StoredMetadata decodeMetadata(String value) {
        String[] fields = value.split("\\|", -1);
        if (fields.length != 6) {
            throw new IllegalArgumentException("Invalid presence metadata");
        }
        return new StoredMetadata(
                Long.parseLong(fields[0]),
                fields[1],
                Double.parseDouble(fields[2]),
                fields[3].isEmpty() ? null : Double.parseDouble(fields[3]),
                Boolean.parseBoolean(fields[4]),
                OffsetDateTime.parse(fields[5]));
    }

    private record GeoCandidate(String member, Point point) {
    }

    private record StoredMetadata(
            long userId,
            String mode,
            double accuracy,
            Double heading,
            boolean stationary,
            OffsetDateTime updatedAt
    ) {
    }

    @Override
    public List<Double> appendDistanceHistory(
            long sessionId,
            long otherSessionId,
            double distanceMeters
    ) {
        String key = HISTORY_KEY_PREFIX + sessionId;
        String field = Long.toString(otherSessionId);
        String stored = redisTemplate.execute(
                APPEND_HISTORY_SCRIPT,
                List.of(key),
                field,
                Double.toString(distanceMeters),
                Long.toString(HISTORY_TTL.toSeconds())
        );
        if (stored == null || stored.isBlank()) {
            return List.of(distanceMeters);
        }
        List<Double> history = new ArrayList<>();
        for (String value : stored.split(",")) {
            history.add(Double.parseDouble(value));
        }
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
        cleanupExpiredMembers();
        String member = Long.toString(sessionId);
        Object stored = redisTemplate.opsForHash().get(METADATA_KEY, member);
        List<Point> positions = redisTemplate.opsForGeo().position(GEO_KEY, member);
        if (stored == null || positions == null || positions.isEmpty() || positions.getFirst() == null) {
            return Optional.empty();
        }
        try {
            Point point = positions.getFirst();
            StoredMetadata metadata = decodeMetadata(stored.toString());
            return Optional.of(new PresenceLocation(
                    sessionId,
                    metadata.userId(),
                    metadata.mode(),
                    point.getX(),
                    point.getY(),
                    metadata.accuracy(),
                    metadata.heading(),
                    metadata.stationary(),
                    metadata.updatedAt()
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
        redisTemplate.opsForHash().delete(METADATA_KEY, member);
        redisTemplate.delete(List.of(
                AUTH_SESSION_KEY_PREFIX + member,
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
        redisTemplate.opsForHash().delete(METADATA_KEY, expired.toArray());
        redisTemplate.delete(expired.stream().map(member -> SESSION_KEY_PREFIX + member).toList());
    }
}
