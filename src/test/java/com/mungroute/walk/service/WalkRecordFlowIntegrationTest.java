package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.walk.dto.request.SaveWalkRequest;
import com.mungroute.walk.dto.response.WalkRecordDetailResponse;
import com.mungroute.walk.exception.WalkErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

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
    JdbcTemplate jdbcTemplate;

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
