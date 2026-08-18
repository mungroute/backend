package com.mungroute.meet.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record MeetPresenceResponse(
        long sessionId,
        OffsetDateTime updatedAt,
        int nextUpdateAfterSeconds,
        List<MeetCandidateResponse> candidates,
        MeetConnectionResponse connection
) {
}
