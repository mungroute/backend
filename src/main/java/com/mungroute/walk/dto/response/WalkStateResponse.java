package com.mungroute.walk.dto.response;

import java.time.OffsetDateTime;

public record WalkStateResponse(
        Long sessionId,
        String status,
        OffsetDateTime changedAt
) {
}
