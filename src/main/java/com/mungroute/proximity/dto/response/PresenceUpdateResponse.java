package com.mungroute.proximity.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PresenceUpdateResponse(
        long sessionId,
        OffsetDateTime updatedAt,
        int nextUpdateAfterSeconds,
        List<NearbyPresenceResponse> nearby,
        String clientMessageId
) {
    public PresenceUpdateResponse(
            long sessionId,
            OffsetDateTime updatedAt,
            int nextUpdateAfterSeconds,
            List<NearbyPresenceResponse> nearby
    ) {
        this(sessionId, updatedAt, nextUpdateAfterSeconds, nearby, null);
    }
}
