package com.mungroute.proximity.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PresenceUpdateResponse(
        long sessionId,
        OffsetDateTime updatedAt,
        int nextUpdateAfterSeconds,
        List<NearbyPresenceResponse> nearby
) {
}
