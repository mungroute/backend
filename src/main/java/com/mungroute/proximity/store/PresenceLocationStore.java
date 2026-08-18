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

    NearbyPresenceTransition synchronizeNearbySessions(
            long sessionId,
            List<Long> nearbySessionIds
    );

    String issueMeetCandidateRef(long viewerSessionId, long targetSessionId, long targetUserId);

    Optional<MeetCandidateTarget> resolveMeetCandidateRef(long viewerSessionId, String candidateRef);

    Optional<PresenceLocation> findLocation(long sessionId);

    void delete(long sessionId);
}
