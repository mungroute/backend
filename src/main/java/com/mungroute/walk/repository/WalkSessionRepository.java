package com.mungroute.walk.repository;

import com.mungroute.walk.domain.WalkSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface WalkSessionRepository extends JpaRepository<WalkSession, Long> {
    // 해당 사용자에게 종료되지 않은 산책이 있는지 확인한다.
    boolean existsByUser_UserIdAndEndedAtIsNull(Long userId);


    // GPS 포인트 추가와 산책 종료 시 세션 행을 쓰기 잠금으로 조회 (Read Only)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ws
            FROM WalkSession ws
            WHERE ws.sessionId = :sessionId
            """)
    Optional<WalkSession> findByIdForUpdate(
            @Param("sessionId") Long sessionId
    );

    /**
     * usable point를 시간순으로 연결해 거리와 LineString을 계산하고 세션을 종료한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    WITH usable_ordered AS (
                        SELECT
                            point_id,
                            recorded_at,
                            location,
                            LAG(location) OVER (
                                ORDER BY recorded_at, point_id
                            ) AS previous_location
                        FROM walk_track_point
                        WHERE session_id = :sessionId
                          AND accuracy_m <= 40.0
                    ),
                    stats AS (
                        SELECT
                            COUNT(*) AS usable_count,
                            COUNT(DISTINCT ST_AsEWKB(location))
                                AS distinct_location_count,
                            COALESCE(
                                SUM(
                                    ST_Distance(
                                        previous_location,
                                        location
                                    )
                                ) FILTER (
                                    WHERE previous_location IS NOT NULL
                                ),
                                0.0
                            ) AS raw_distance_m,
                            CASE
                                WHEN COUNT(DISTINCT ST_AsEWKB(location)) >= 2
                                THEN ST_MakeLine(
                                    location
                                    ORDER BY recorded_at, point_id
                                )
                                ELSE NULL
                            END AS track_geom
                        FROM usable_ordered
                    )
                    UPDATE walk_session AS session
                    SET
                        ended_at = :endedAt,
                        distance_m = CASE
                            WHEN stats.distinct_location_count >= 2
                            THEN ROUND(stats.raw_distance_m::numeric, 1)
                            ELSE 0.0
                        END,
                        duration_sec = FLOOR(
                            EXTRACT(
                                EPOCH FROM (:endedAt - session.started_at)
                            )
                        )::integer,
                        track_geom = stats.track_geom
                    FROM stats
                    WHERE session.session_id = :sessionId
                      AND session.ended_at IS NULL
                    """,
            nativeQuery = true
    )
    int finalizeWalkSession(
            @Param("sessionId") Long sessionId,
            @Param("endedAt") OffsetDateTime endedAt
    );

    /**
     * 종료 응답에 필요한 세션 결과와 포인트 개수를 조회한다.
     */
    @Query(
            value = """
                    SELECT
                        session.session_id AS "sessionId",
                        session.ended_at AS "endedAt",
                        COALESCE(session.distance_m, 0.0) AS "distanceM",
                        COALESCE(session.duration_sec, 0) AS "durationSec",
                        COUNT(point.point_id) AS "pointCount",
                        COUNT(point.point_id) FILTER (
                            WHERE point.accuracy_m <= 40.0
                        ) AS "usablePointCount",
                        COUNT(DISTINCT ST_AsEWKB(point.location)) FILTER (
                            WHERE point.accuracy_m <= 40.0
                        ) AS "distinctUsableLocationCount"
                    FROM walk_session AS session
                    LEFT JOIN walk_track_point AS point
                      ON point.session_id = session.session_id
                    WHERE session.session_id = :sessionId
                    GROUP BY
                        session.session_id,
                        session.ended_at,
                        session.distance_m,
                        session.duration_sec
                    """,
            nativeQuery = true
    )
    Optional<EndWalkSummary> findEndWalkSummary(
            @Param("sessionId") Long sessionId
    );
}
