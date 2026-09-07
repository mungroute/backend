package com.mungroute.proximity.port;

import java.util.Optional;

/** Walkable-network snapping required by the safe-detour use case. */
public interface SafeDetourSnapPort {
    Optional<SnappedPoint> snap(double latitude, double longitude, double radiusMeters);

    record SnappedPoint(
            long nodeId,
            long segmentId,
            double latitude,
            double longitude,
            double distanceMeters
    ) {
    }
}
