package com.mungroute.walk.repository;

import java.math.BigDecimal;
import java.time.Instant;

// 종료된 산책의 저장 결과와 GPS 포인트 집계값을 조회한다.
public interface EndWalkSummary {

    Long getSessionId();

    Instant getEndedAt();

    BigDecimal getDistanceM();

    Integer getDurationSec();

    Long getPointCount();

    Long getUsablePointCount();

    Long getDistinctUsableLocationCount();
}
