package com.mungroute.walk.dto.response;

import com.mungroute.walk.domain.WalkMatchStatus;
import com.mungroute.walk.repository.EndWalkSummary;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// 산책 종료 결과를 표현한다.

public record EndWalkResponse(
        Long sessionId,
        OffsetDateTime endedAt,
        BigDecimal distanceM,
        Integer durationSec,
        Long pointCount,
        Long usablePointCount,
        WalkMatchStatus matchStatus
) {

    //DB 종료 집계 결과를 API 응답으로 변환
    public static EndWalkResponse from(EndWalkSummary summary) {
        WalkMatchStatus matchStatus = determineMatchStatus(summary);

        return new EndWalkResponse(
                summary.getSessionId(),
                summary.getEndedAt().atOffset(ZoneOffset.UTC),
                summary.getDistanceM(),
                summary.getDurationSec(),
                summary.getPointCount(),
                summary.getUsablePointCount(),
                matchStatus
        );
    }

    // D2 기준으로 포인트가 부족한지, 맵매칭 대기 상태인지 결정
    private static WalkMatchStatus determineMatchStatus(
            EndWalkSummary summary
    ) {
        if (summary.getUsablePointCount() < 2
                || summary.getDistinctUsableLocationCount() < 2) {
            return WalkMatchStatus.INSUFFICIENT_POINTS;
        }

        return WalkMatchStatus.NOT_PERFORMED;
    }
}
