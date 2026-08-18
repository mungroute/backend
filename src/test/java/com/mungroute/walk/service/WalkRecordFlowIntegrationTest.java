package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.walk.dto.request.SaveWalkRequest;
import com.mungroute.walk.dto.request.StartWalkRequest;
import com.mungroute.walk.dto.request.RenameWalkRequest;
import com.mungroute.walk.dto.response.WalkRecordDetailResponse;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.proximity.repository.PresenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class WalkRecordFlowIntegrationTest {
    @Autowired
    WalkFinalizationService finalizationService;

    @Autowired
    WalkRecordService recordService;

    @Autowired
    WalkSessionService sessionService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PresenceRepository presenceRepository;

    @Test
    void finalizationExcludesAccumulatedAndOpenPauseTime() {
        long userId = insertUser();
        OffsetDateTime startedAt = OffsetDateTime.now().minusMinutes(10);
        long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO walk_session(
                    user_id, started_at, paused_at, paused_duration_sec, mode
                ) VALUES (?, ?, ?, 120, 'off')
                RETURNING session_id
                """, Long.class, userId, startedAt, startedAt.plusMinutes(8));

        WalkFinalizationService.FinalizationResult result = finalizationService.finalizeWalk(
                userId,
                sessionId,
                startedAt.plusMinutes(10)
        );

        assertThat(result.finalizedNow()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT duration_sec FROM walk_session WHERE session_id = ?",
                Integer.class,
                sessionId
        )).isEqualTo(360);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT paused_duration_sec FROM walk_session WHERE session_id = ?",
                Integer.class,
                sessionId
        )).isEqualTo(240);
    }

    @Test
    void savesListsDetailsMarksRepresentativeAndDeletesEligibleLoop() {
        long userId = insertUser();
        long sessionId = insertEndedSession(userId, "MATCHED", true, "{101,102,103}");

        WalkRecordDetailResponse saved = recordService.save(
                userId,
                sessionId,
                new SaveWalkRequest("저녁 공원길", true)
        );

        assertThat(saved.courseName()).isEqualTo("저녁 공원길");
        assertThat(saved.representative()).isTrue();
        assertThat(saved.matchedSegmentIds()).containsExactly(101L, 102L, 103L);
        assertThat(recordService.list(userId, 0, 20))
                .extracting("sessionId")
                .containsExactly(sessionId);

        recordService.delete(userId, sessionId);

        assertThat(recordService.list(userId, 0, 20)).isEmpty();
    }

    @Test
    void preservesFailedMatchRecordButRejectsRepresentativeDesignation() {
        long userId = insertUser();
        long sessionId = insertEndedSession(userId, "FAILED", false, null);

        WalkRecordDetailResponse saved = recordService.save(
                userId,
                sessionId,
                new SaveWalkRequest("매칭 실패 산책", false)
        );

        assertThat(saved.matchStatus().name()).isEqualTo("FAILED");
        assertThat(saved.matchedSegmentIds()).isEmpty();
        assertThatThrownBy(() -> recordService.setRepresentative(userId, sessionId, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(WalkErrorCode.REPRESENTATIVE_COURSE_INELIGIBLE));
    }

    @Test
    void filtersByDogRenamesAndAggregatesMonthlyStatistics() {
        long userId = insertUser();
        long dogId = jdbcTemplate.queryForObject("""
                INSERT INTO dog_profile(user_id, name, breed, birth_date, is_default)
                VALUES (?, '망고', '골든 리트리버', CURRENT_DATE - interval '4 years', true)
                RETURNING dog_id
                """, Long.class, userId);
        long sessionId = insertEndedSession(userId, "MATCHED", true, "{101,102}");
        recordService.save(userId, sessionId, new SaveWalkRequest("점심 산책", false));
        jdbcTemplate.update("""
                INSERT INTO walk_session_dog(session_id, dog_id, dog_name, breed)
                VALUES (?, ?, '망고', '골든 리트리버')
                """, sessionId, dogId);

        var renamed = recordService.rename(
                userId, sessionId, new RenameWalkRequest("망고의 점심 산책"));
        var now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
        var filtered = recordService.list(
                userId, 0, 20, now.minusDays(1), now.plusDays(1), dogId);
        var statistics = recordService.statistics(
                userId, YearMonth.from(now), dogId);

        assertThat(renamed.courseName()).isEqualTo("망고의 점심 산책");
        assertThat(renamed.dogs()).extracting("name").containsExactly("망고");
        assertThat(filtered).hasSize(1);
        assertThat(filtered.getFirst().dogNames()).containsExactly("망고");
        assertThat(filtered.getFirst().routePreviewGeoJson()).isNotNull();
        assertThat(statistics.walkCount()).isOne();
        assertThat(statistics.totalDistanceM()).isEqualByComparingTo("1800.0");
        assertThat(statistics.favoriteCourse().courseName()).isEqualTo("망고의 점심 산책");
        assertThat(statistics.weekdayDistances()).hasSize(1);
    }

    @Test
    void aggregatesSavedWalksByKoreanCalendarDayForContributionExploration() {
        long userId = insertUser();
        long morningSessionId = insertEndedSession(userId, "MATCHED", true, "{101}");
        long eveningSessionId = insertEndedSession(userId, "MATCHED", true, "{102}");
        OffsetDateTime morning = OffsetDateTime.parse("2026-08-03T08:30:00+09:00");
        OffsetDateTime evening = OffsetDateTime.parse("2026-08-03T19:10:00+09:00");
        jdbcTemplate.update(
                "UPDATE walk_session SET started_at = ?, ended_at = ?, distance_m = 650.0 WHERE session_id = ?",
                morning.minusMinutes(20), morning, morningSessionId);
        jdbcTemplate.update(
                "UPDATE walk_session SET started_at = ?, ended_at = ?, distance_m = 1350.0, track_geom = NULL WHERE session_id = ?",
                evening.minusMinutes(30), evening, eveningSessionId);
        recordService.save(userId, morningSessionId, new SaveWalkRequest("아침 산책", false));
        recordService.save(userId, eveningSessionId, new SaveWalkRequest("저녁 산책", false));

        var contributions = recordService.contributions(userId, Year.of(2026), null);

        assertThat(contributions.year()).isEqualTo(2026);
        assertThat(contributions.days()).singleElement().satisfies(day -> {
            assertThat(day.date()).isEqualTo(java.time.LocalDate.of(2026, 8, 3));
            assertThat(day.totalDistanceM()).isEqualByComparingTo("2000.0");
            assertThat(day.walkCount()).isEqualTo(2);
            assertThat(day.records()).extracting("courseName")
                    .containsExactly("아침 산책", "저녁 산책");
            assertThat(day.records()).extracting("hasRoute")
                    .containsExactly(true, false);
        });
    }

    @Test
    void storesAnonymousProximityNotificationsAndExposesTheirCountInTheWalkRecord() {
        long recipientUserId = insertUser();
        long otherUserId = insertUser();
        long recipientSessionId = insertEndedSession(recipientUserId, "MATCHED", true, "{101}");
        long otherSessionId = insertEndedSession(otherUserId, "MATCHED", true, "{102}");
        recordService.save(
                recipientUserId,
                recipientSessionId,
                new SaveWalkRequest("거리두기 기록 산책", false)
        );
        OffsetDateTime firstAt = OffsetDateTime.now();

        presenceRepository.recordProximityNotification(
                recipientSessionId, otherSessionId, "BAND_100_500", 2, 45, "NEW", firstAt);
        presenceRepository.recordProximityNotification(
                recipientSessionId, otherSessionId, "BAND_30_50", 2, 45, "APPROACHING", firstAt.plusSeconds(4));
        presenceRepository.endProximityEvents(
                recipientSessionId, List.of(otherSessionId), firstAt.plusSeconds(8));
        presenceRepository.recordProximityNotification(
                recipientSessionId, otherSessionId, "VERY_CLOSE", null, null, "NEW", firstAt.plusSeconds(12));

        var detail = recordService.detail(recipientUserId, recipientSessionId);
        var rows = jdbcTemplate.queryForList("""
                SELECT distance_band, bearing_octant, bearing_spread, trend,
                       notify_count, active, ended_at
                FROM proximity_event
                WHERE recipient_session_id = ?
                ORDER BY proximity_event_id
                """, recipientSessionId);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().get("notify_count")).isEqualTo(2);
        assertThat(rows.getFirst().get("active")).isEqualTo(false);
        assertThat(rows.getFirst().get("ended_at")).isNotNull();
        assertThat(rows.getLast().get("distance_band")).isEqualTo("VERY_CLOSE");
        assertThat(rows.getLast().get("bearing_octant")).isNull();
        assertThat(rows.getLast().get("bearing_spread")).isNull();
        assertThat(detail.distanceAlertCount()).isEqualTo(3);
    }

    @Test
    void resumesAnEmptyActiveSessionWhenStartingAgain() {
        long userId = insertUser();
        long orphanedSessionId = jdbcTemplate.queryForObject("""
                INSERT INTO walk_session(user_id, started_at, mode)
                VALUES (?, now() - interval '5 minutes', 'off')
                RETURNING session_id
                """, Long.class, userId);

        var started = sessionService.startWalk(userId, new StartWalkRequest("distance"));

        assertThat(started.sessionId()).isEqualTo(orphanedSessionId);
        assertThat(started.mode()).isEqualTo("off");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk_session WHERE session_id = ?",
                Integer.class,
                orphanedSessionId
        )).isOne();
    }

    @Test
    void resumesAnActiveSessionThatAlreadyContainsGpsPoints() {
        long userId = insertUser();
        long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO walk_session(user_id, started_at, mode)
                VALUES (?, now() - interval '5 minutes', 'off')
                RETURNING session_id
                """, Long.class, userId);
        jdbcTemplate.update("""
                INSERT INTO walk_track_point(session_id, recorded_at, location, accuracy_m)
                VALUES (?, now(), ST_Transform(ST_SetSRID(ST_MakePoint(126.978, 37.5665), 4326), 5186), 5.0)
                """, sessionId);

        var started = sessionService.startWalk(userId, new StartWalkRequest("distance"));

        assertThat(started.sessionId()).isEqualTo(sessionId);
        assertThat(started.mode()).isEqualTo("off");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk_track_point WHERE session_id = ?",
                Integer.class,
                sessionId
        )).isOne();
    }

    @Test
    void resumesAPausedActiveSessionBeforeReturningIt() {
        long userId = insertUser();
        long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO walk_session(user_id, started_at, paused_at, mode, locked_mode)
                VALUES (?, now() - interval '5 minutes', now() - interval '1 minute', 'distance', 'distance')
                RETURNING session_id
                """, Long.class, userId);

        var started = sessionService.startWalk(userId, new StartWalkRequest("off"));

        assertThat(started.sessionId()).isEqualTo(sessionId);
        assertThat(started.mode()).isEqualTo("distance");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT paused_at IS NULL FROM walk_session WHERE session_id = ?",
                Boolean.class,
                sessionId
        )).isTrue();
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user(email, nickname, password_hash, phone_number)
                VALUES (?, ?, 'integration-test-hash', ?)
                RETURNING user_id
                """, Long.class, "d7-" + suffix + "@example.com", "d7-" + suffix, "010" + suffix);
    }

    private long insertEndedSession(long userId, String matchStatus, boolean loop, String segments) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO walk_session(
                    user_id, started_at, ended_at, mode, distance_m, duration_sec,
                    matched_segments, is_loop, match_status, track_geom
                ) VALUES (
                    ?, now() - interval '30 minutes', now(), 'off', 1800.0, 1800,
                    CAST(? AS BIGINT[]), ?, ?,
                    ST_GeomFromText('LINESTRING(198000 451000, 198100 451100)', 5186)
                )
                RETURNING session_id
                """, Long.class, userId, segments, loop, matchStatus);
    }
}
