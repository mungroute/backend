package com.mungroute.meet.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeetRequestResponse(
        UUID requestId,
        String direction,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        MeetProfilePreviewResponse preview,
        MeetProfileResponse profile
) {
}
