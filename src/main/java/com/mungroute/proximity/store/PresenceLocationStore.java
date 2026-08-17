package com.mungroute.proximity.store;

import java.util.List;
import java.util.Optional;

public interface PresenceLocationStore {

    void update(PresenceLocation location);

    Optional<PresenceSessionState> findSession(long sessionId);

    List<NearbyPresenceLocation> findNearby(
            PresenceLocation origin,
            int radiusMeters,
            int candidateLimit
    );

    List<Double> appendDistanceHistory(
            long sessionId,
            long otherSessionId,
            double distanceMeters
    );

    void delete(long sessionId);
}
