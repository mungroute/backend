package com.mungroute.proximity.repository;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;

public interface PresenceRepository {

    int upsertConsent(
            long sessionId,
            long userId,
            String mode,
            OffsetDateTime consentedAt
    );

    boolean hasConsent(long sessionId);

    int updateTelemetry(
            long sessionId,
            BigDecimal accuracy,
            BigDecimal heading,
            boolean stationary,
            OffsetDateTime updatedAt
    );

    int updateMode(
            long sessionId,
            String mode,
            OffsetDateTime updatedAt
    );

    int deleteBySessionId(long sessionId);

    int recordProximityNotification(
            long recipientSessionId,
            long otherSessionId,
            String distanceBand,
            Integer bearingOctant,
            Integer bearingSpread,
            String trend,
            OffsetDateTime notifiedAt
    );

    int endProximityEvents(
            long recipientSessionId,
            List<Long> otherSessionIds,
            OffsetDateTime endedAt
    );

    int endAllProximityEvents(long sessionId, OffsetDateTime endedAt);
}
