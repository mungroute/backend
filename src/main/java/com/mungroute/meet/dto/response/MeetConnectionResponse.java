package com.mungroute.meet.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MeetConnectionResponse(
        UUID requestId,
        BigDecimal lon,
        BigDecimal lat,
        OffsetDateTime updatedAt,
        MeetProfileResponse profile
) {
}
