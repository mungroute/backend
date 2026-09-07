package com.mungroute.meet.port;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Presence capabilities consumed by meet use cases. */
public interface MeetPresencePort {
    void update(Location location);

    Optional<SessionState> findSession(long sessionId);

    void cacheSession(SessionState session);

    List<NearbyLocation> findNearby(Location origin, int radiusMeters, int candidateLimit);

    String issueCandidateRef(long viewerSessionId, long targetSessionId, long targetUserId);

    Optional<CandidateTarget> resolveCandidateRef(long viewerSessionId, String candidateRef);

    Optional<Location> findLocation(long sessionId);

    boolean hasConsent(long sessionId);

    int updateTelemetry(
            long sessionId,
            BigDecimal accuracy,
            BigDecimal heading,
            boolean stationary,
            OffsetDateTime updatedAt
    );

    record Location(
            long sessionId,
            long userId,
            String mode,
            double longitude,
            double latitude,
            double accuracyMeters,
            Double headingDegrees,
            boolean stationary,
            OffsetDateTime updatedAt
    ) {
    }

    record NearbyLocation(
            long sessionId,
            long userId,
            String mode,
            double longitude,
            double latitude,
            double accuracyMeters,
            Double headingDegrees,
            boolean stationary
    ) {
    }

    record SessionState(long sessionId, long userId, String mode) {
    }

    record CandidateTarget(long sessionId, long userId) {
    }
}
