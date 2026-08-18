package com.mungroute.walk.dto.response;

import com.mungroute.walk.domain.WalkMatchStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record WalkRecordSummaryResponse(
        Long sessionId,
        String courseName,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        BigDecimal distanceM,
        Integer durationSec,
        boolean representative,
        Boolean loop,
        WalkMatchStatus matchStatus,
        List<String> dogNames,
        long distanceAlertCount,
        BigDecimal averageSpeedKmh,
        JsonNode routePreviewGeoJson
) {
}
