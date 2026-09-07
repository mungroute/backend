package com.mungroute.group.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.group.dto.request.CreateGroupRequest;
import com.mungroute.group.dto.request.ShareCourseRequest;
import com.mungroute.group.exception.GroupErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
@Sql(scripts = "/sql/route-network-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/route-network-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class GroupFlowIntegrationTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-18T05:00:00Z");

    @Autowired
    GroupService groupService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void createsInvitesSharesSavesAndRevokesAccessAfterLeaving() {
        long ownerId = insertUser("owner");
        long memberId = insertUser("member");
        long visitorId = insertUser("visitor");
        long courseId = insertCustomCourse(ownerId);

        var group = groupService.create(ownerId, new CreateGroupRequest("남산 산책단", "함께 걸어요"), REQUESTED_AT);
        assertThat(groupService.list(ownerId))
                .extracting("groupId")
                .containsExactly(group.groupId());
        var invite = groupService.issueInvite(ownerId, group.groupId());
        var joined = groupService.join(memberId, invite.inviteCode(), REQUESTED_AT);

        assertThat(joined.myRole()).isEqualTo("MEMBER");
        assertThat(joined.memberCount()).isEqualTo(2);

        var shared = groupService.shareCourse(
                ownerId, group.groupId(), new ShareCourseRequest("custom", courseId), REQUESTED_AT
        );
        assertThat(groupService.courses(memberId, group.groupId(), "latest", 20, REQUESTED_AT))
                .extracting("sharedCourseId").containsExactly(shared.sharedCourseId());

        var saved = groupService.saveSharedCourse(memberId, group.groupId(), shared.sharedCourseId());
        assertThat(saved.courseSource()).isEqualTo("custom");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_course WHERE custom_course_id = ? AND user_id = ?",
                Integer.class, saved.courseId(), memberId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT course_name FROM custom_course WHERE custom_course_id = ?",
                String.class, saved.courseId()
        )).endsWith(" (그룹)").doesNotEndWith(" (그룹) (그룹)");
        assertThatThrownBy(() -> groupService.shareCourse(
                memberId, group.groupId(), new ShareCourseRequest("custom", saved.courseId()), REQUESTED_AT
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_SAVED_COURSE_RESHARE_NOT_ALLOWED));
        assertThat(groupService.activities(memberId, group.groupId(), null, 20))
                .extracting("activityType")
                .contains("GROUP_CREATED", "MEMBER_JOINED", "COURSE_SHARED", "COURSE_SAVED");

        long memberCourseId = insertCustomCourse(memberId);
        var memberShared = groupService.shareCourse(
                memberId, group.groupId(), new ShareCourseRequest("custom", memberCourseId), REQUESTED_AT
        );
        assertThat(groupService.courses(ownerId, group.groupId(), "latest", 20, REQUESTED_AT))
                .extracting("sharedCourseId")
                .contains(memberShared.sharedCourseId());

        var publicGroup = groupService.create(ownerId, new CreateGroupRequest(
                "공개 산책단", "누구나 함께 걸어요", "PUBLIC", "OPEN"
        ), REQUESTED_AT);
        assertThat(groupService.discover(visitorId))
                .extracting("groupId")
                .contains(publicGroup.groupId());
        assertThat(groupService.joinOpen(visitorId, publicGroup.groupId(), REQUESTED_AT).myRole())
                .isEqualTo("MEMBER");
        assertThat(groupService.discover(visitorId))
                .extracting("groupId")
                .doesNotContain(publicGroup.groupId());

        groupService.leave(memberId, group.groupId());
        assertThat(groupService.courses(ownerId, group.groupId(), "latest", 20, REQUESTED_AT))
                .extracting("sharedCourseId")
                .doesNotContain(memberShared.sharedCourseId());
        assertThatThrownBy(() -> groupService.detail(memberId, group.groupId(), REQUESTED_AT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_ACCESS_DENIED));
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO app_user(email, nickname, password_hash, phone_number)
                VALUES (?, ?, 'encoded', ?)
                RETURNING user_id
                """, Long.class, prefix + suffix + "@example.com", prefix + suffix, "010" + Math.abs(suffix.hashCode()));
        return id == null ? 0 : id;
    }

    private long insertCustomCourse(long userId) {
        Long id = jdbcTemplate.queryForObject("""
                WITH chosen AS (
                    SELECT segment_id, source, target, length_m, geom,
                           COALESCE(shade_ratio_12, 0.5) AS shade_ratio,
                           COALESCE(surface_temp_12_c, 30) AS surface_temp,
                           COALESCE(thermal_weather_date, current_date) AS weather_date,
                           COALESCE(thermal_model_confidence, 'LOW') AS confidence
                    FROM route_segment
                    WHERE length_m > 0
                    ORDER BY segment_id
                    LIMIT 1
                )
                INSERT INTO custom_course(
                    user_id, course_name, waypoints, segment_ids, segment_lengths_m, geom,
                    length_m, duration_min, shade_ratio, estimated_surface_temp_c,
                    reference_hour, thermal_weather_date, thermal_model_confidence,
                    is_loop, is_representative
                )
                SELECT ?, '그룹 공유 테스트 코스',
                       jsonb_build_array(
                           jsonb_build_object('snapped', jsonb_build_object('lat', 37.5, 'lon', 126.9)),
                           jsonb_build_object('snapped', jsonb_build_object('lat', 37.6, 'lon', 127.0))
                       ),
                       ARRAY[segment_id], ARRAY[length_m::numeric(10,3)], geom,
                       length_m::numeric(10,2), 5, shade_ratio, surface_temp,
                       12, weather_date, confidence, false, false
                FROM chosen
                RETURNING custom_course_id
                """, Long.class, userId);
        if (id == null) throw new IllegalStateException("테스트 코스를 만들 수 없습니다.");
        return id;
    }
}
