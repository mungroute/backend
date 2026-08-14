package com.mungroute.walk.dto.response;

import com.mungroute.walk.domain.WalkMatchStatus;
import com.mungroute.walk.repository.EndWalkSummary;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

// 산책 종료 결과를 표현한다.

public record EndWalkResponse(
        Long sessionId,
        OffsetDateTime endedAt,
        BigDecimal distanceM,
        Integer durationSec,
        Long pointCount,
        Long usablePointCount,
        WalkMatchStatus matchStatus,
        String matchFailureReason,
        List<Long> matchedSegmentIds,
        Boolean isLoop,
        JsonNode trackGeoJson
) {

    //DB 종료 집계 결과를 API 응답으로 변환
    public static EndWalkResponse from(EndWalkSummary summary, ObjectMapper objectMapper) {
        return new EndWalkResponse(
                summary.getSessionId(),
                summary.getEndedAt().atOffset(ZoneOffset.UTC),
                summary.getDistanceM(),
                summary.getDurationSec(),
                summary.getPointCount(),
                summary.getUsablePointCount(),
                WalkMatchStatus.valueOf(summary.getMatchStatus()),
                summary.getMatchFailureReason(),
                parseSegmentIds(summary.getMatchedSegmentIdsCsv()),
                summary.getIsLoop(),
                parseGeoJson(summary.getTrackGeoJson(), objectMapper)
        );
    }

    private static List<Long> parseSegmentIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(Long::valueOf)
                .toList();
    }

    private static JsonNode parseGeoJson(String geoJson, ObjectMapper objectMapper) {
        if (geoJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(geoJson);
        } catch (JacksonException exception) {
            throw new IllegalStateException("DB의 산책 GeoJSON을 해석할 수 없습니다.", exception);
        }
    }
}
