package com.mungroute.walk.dto.response;

import java.time.OffsetDateTime;

public record ChangeWalkModeResponse(
        Long sessionId,
        String mode,
        String lockedMode,
        OffsetDateTime changedAt
) {
}
