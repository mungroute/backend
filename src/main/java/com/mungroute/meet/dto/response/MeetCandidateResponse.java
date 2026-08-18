package com.mungroute.meet.dto.response;

import java.time.OffsetDateTime;

public record MeetCandidateResponse(String candidateRef, String distanceBand, OffsetDateTime expiresAt) {
}
