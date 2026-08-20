package com.mungroute.course.recommendation.repository;

import com.mungroute.course.recommendation.dto.CourseRecommendationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCourseRecommendationStore implements CourseRecommendationStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCourseRecommendationStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveCompleted(
            UUID requestId,
            long userId,
            int targetDurationMin,
            OffsetDateTime departureAt,
            double startLat,
            double startLon,
            CourseRecommendationResponse response,
            OffsetDateTime expiresAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO course_recommendation_request(
                    request_id, user_id, target_duration_min, departure_at,
                    start_geom, status, response_payload, expires_at
                ) VALUES (
                    ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326),
                    'COMPLETED', ?::jsonb, ?
                )
                """,
                requestId, userId, targetDurationMin, departureAt,
                startLon, startLat, write(response), expiresAt
        );
    }

    @Override
    public Optional<CourseRecommendationResponse> findOwned(long userId, UUID requestId, OffsetDateTime now) {
        return jdbcTemplate.query("""
                SELECT response_payload::text
                FROM course_recommendation_request
                WHERE request_id = ? AND user_id = ? AND expires_at > ? AND status = 'COMPLETED'
                """, (resultSet, rowNumber) -> read(resultSet.getString(1)), requestId, userId, now)
                .stream().findFirst();
    }

    private String write(CourseRecommendationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException("추천 응답을 저장할 수 없습니다.", exception);
        }
    }

    private CourseRecommendationResponse read(String payload) {
        try {
            return objectMapper.readValue(payload, CourseRecommendationResponse.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("저장된 추천 응답을 읽을 수 없습니다.", exception);
        }
    }
}
