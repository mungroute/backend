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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ws
            FROM WalkSession ws
            WHERE ws.user.userId = :userId
              AND ws.endedAt IS NULL
            """)
    Optional<WalkSession> findActiveByUserIdForUpdate(@Param("userId") Long userId);


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
                        duration_sec = GREATEST(
                            0,
                            FLOOR(EXTRACT(EPOCH FROM (:endedAt - session.started_at)))::integer
                            - session.paused_duration_sec
                            - CASE
                                WHEN session.paused_at IS NULL THEN 0
                                ELSE GREATEST(
                                    0,
                                    FLOOR(EXTRACT(EPOCH FROM (:endedAt - session.paused_at)))::integer
                                )
                              END
                        ),
                        track_geom = stats.track_geom,
                        paused_duration_sec = session.paused_duration_sec
                            + CASE
                                WHEN session.paused_at IS NULL THEN 0
                                ELSE GREATEST(
                                    0,
                                    FLOOR(EXTRACT(EPOCH FROM (:endedAt - session.paused_at)))::integer
                                )
                              END,
                        paused_at = NULL
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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE walk_session
            SET paused_at = :pausedAt
            WHERE session_id = :sessionId
              AND ended_at IS NULL
              AND paused_at IS NULL
            """, nativeQuery = true)
    int pauseWalkSession(
            @Param("sessionId") Long sessionId,
            @Param("pausedAt") OffsetDateTime pausedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE walk_session
            SET paused_duration_sec = paused_duration_sec
                    + GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (:resumedAt - paused_at)))::integer),
                paused_at = NULL
            WHERE session_id = :sessionId
              AND ended_at IS NULL
              AND paused_at IS NOT NULL
            """, nativeQuery = true)
    int resumeWalkSession(
            @Param("sessionId") Long sessionId,
            @Param("resumedAt") OffsetDateTime resumedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE walk_session
            SET match_status = :matchStatus,
                match_failure_reason = :failureReason,
                matched_at = :matchedAt
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    int updateMatchOutcome(
            @Param("sessionId") Long sessionId,
            @Param("matchStatus") String matchStatus,
            @Param("failureReason") String failureReason,
            @Param("matchedAt") OffsetDateTime matchedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE walk_session
            SET is_saved = true,
                course_name = :courseName
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    int saveWalkRecord(
            @Param("sessionId") Long sessionId,
            @Param("courseName") String courseName
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE walk_session
            SET is_representative = false
            WHERE user_id = :userId
              AND is_representative = true
            """, nativeQuery = true)
    int clearRepresentativeWalks(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE custom_course
            SET is_representative = false, updated_at = now()
            WHERE user_id = :userId
              AND is_representative = true
            """, nativeQuery = true)
    int clearCustomRepresentativeCourses(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE walk_session
            SET is_representative = :representative
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    int setRepresentative(
            @Param("sessionId") Long sessionId,
            @Param("representative") boolean representative
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
                        ) AS "distinctUsableLocationCount",
                        session.match_status AS "matchStatus",
                        session.match_failure_reason AS "matchFailureReason",
                        ARRAY_TO_STRING(session.matched_segments, ',') AS "matchedSegmentIdsCsv",
                        session.is_loop AS "isLoop",
                        CASE
                            WHEN session.track_geom IS NULL THEN NULL
                            ELSE ST_AsGeoJSON(ST_Transform(session.track_geom, 4326))
                        END AS "trackGeoJson"
                    FROM walk_session AS session
                    LEFT JOIN walk_track_point AS point
                      ON point.session_id = session.session_id
                    WHERE session.session_id = :sessionId
                    GROUP BY
                        session.session_id,
                        session.ended_at,
                        session.distance_m,
                        session.duration_sec,
                        session.match_status,
                        session.match_failure_reason,
                        session.matched_segments,
                        session.is_loop,
                        session.track_geom
                    """,
            nativeQuery = true
    )
    Optional<EndWalkSummary> findEndWalkSummary(
            @Param("sessionId") Long sessionId
    );
}
