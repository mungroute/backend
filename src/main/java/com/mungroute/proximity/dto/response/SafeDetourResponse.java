package com.mungroute.proximity.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record SafeDetourResponse(
        String requestId,
        String decision,
        String message,
        String firstManeuver,
        Integer addedDistanceM,
        Integer addedDurationSec,
        List<SafeDetourPointResponse> route,
        OffsetDateTime validUntil,
        Integer retryAfterSeconds
) {
    public SafeDetourResponse {
        route = route == null ? List.of() : List.copyOf(route);
    }
}
