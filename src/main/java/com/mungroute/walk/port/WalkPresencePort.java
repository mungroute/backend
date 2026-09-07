package com.mungroute.walk.port;

import java.time.OffsetDateTime;

public interface WalkPresencePort {
    void recordPassiveLocation(
            long sessionId,
            long userId,
            double longitude,
            double latitude,
            double accuracyMeters,
            OffsetDateTime recordedAt
    );

    void remove(long sessionId);

    void pause(long sessionId, OffsetDateTime pausedAt);

    void updateMode(long sessionId, String mode, OffsetDateTime changedAt);
}
