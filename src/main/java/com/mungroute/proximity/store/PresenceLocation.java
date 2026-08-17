package com.mungroute.proximity.store;

import java.time.OffsetDateTime;

public record PresenceLocation(
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
