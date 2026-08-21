package com.mungroute.proximity.store;

public record NearbyPresenceLocation(
        long sessionId,
        long userId,
        String mode,
        double longitude,
        double latitude,
        double accuracyMeters,
        Double headingDegrees,
        boolean stationary
) {
    public NearbyPresenceLocation(
            long sessionId,
            long userId,
            String mode,
            double longitude,
            double latitude,
            double accuracyMeters
    ) {
        this(sessionId, userId, mode, longitude, latitude, accuracyMeters, null, true);
    }
}
