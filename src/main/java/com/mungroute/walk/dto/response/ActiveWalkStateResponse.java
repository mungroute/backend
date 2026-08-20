package com.mungroute.walk.dto.response;

import java.time.OffsetDateTime;

public record ActiveWalkStateResponse(
        Long sessionId,
        String status,
        OffsetDateTime startedAt,
        int elapsedSeconds,
        double distanceM,
        String mode,
        String lockedMode
) {
}
