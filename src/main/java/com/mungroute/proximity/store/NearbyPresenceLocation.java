package com.mungroute.proximity.store;

public record NearbyPresenceLocation(
        long sessionId,
        long userId,
        String mode,
        double longitude,
        double latitude,
        double accuracyMeters
) {
}
