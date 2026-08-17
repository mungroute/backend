package com.mungroute.walk.dto.response;

import tools.jackson.databind.JsonNode;
import com.mungroute.walk.domain.WalkMatchStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record WalkRecordDetailResponse(
        Long sessionId,
        String courseName,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        BigDecimal distanceM,
        Integer durationSec,
        boolean representative,
        Boolean loop,
        WalkMatchStatus matchStatus,
        String matchFailureReason,
        List<Long> matchedSegmentIds,
        Long pointCount,
        Long usablePointCount,
        JsonNode trackGeoJson
) {
}
