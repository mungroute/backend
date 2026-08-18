package com.mungroute.user.repository;

import com.mungroute.user.dto.request.DogProfileRequest;
import com.mungroute.user.dto.response.DogProfileResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class DogProfileRepository {
    private final JdbcTemplate jdbcTemplate;

    public DogProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DogProfileResponse> findAll(long userId) {
        return query("WHERE user_id = ? AND deleted_at IS NULL ORDER BY is_default DESC, dog_id", userId);
    }

    public Optional<DogProfileResponse> find(long userId, long dogId) {
        return query("WHERE user_id = ? AND dog_id = ? AND deleted_at IS NULL", userId, dogId).stream().findFirst();
    }

    public long create(long userId, DogProfileRequest request, boolean makeDefault) {
        Long dogId = jdbcTemplate.queryForObject("""
                INSERT INTO dog_profile(user_id, name, breed, birth_date, profile_image_url,
                                        temperament_tags, gender, neutered, introduction,
                                        leash_greeting, stranger_response, touch_tolerance,
                                        barking_level, biting_level, is_default)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING dog_id
                """, Long.class, userId, request.name().trim(), request.breed().trim(), request.birthDate(),
                normalizeImage(request.profileImageUrl()), request.temperamentTags().toArray(String[]::new),
                request.gender(), request.neutered(), request.introduction(), request.leashGreeting(),
                request.strangerResponse(), request.touchTolerance(), request.barkingLevel(), request.bitingLevel(),
                makeDefault);
        return dogId == null ? 0 : dogId;
    }

    public int update(long userId, long dogId, DogProfileRequest request, boolean makeDefault) {
        return jdbcTemplate.update("""
                UPDATE dog_profile
                SET name = ?, breed = ?, birth_date = ?, profile_image_url = ?, temperament_tags = ?,
                    gender = ?, neutered = ?, introduction = ?, leash_greeting = ?, stranger_response = ?,
                    touch_tolerance = ?, barking_level = ?, biting_level = ?, is_default = ?, updated_at = now()
                WHERE user_id = ? AND dog_id = ? AND deleted_at IS NULL
                """, request.name().trim(), request.breed().trim(), request.birthDate(),
                normalizeImage(request.profileImageUrl()), request.temperamentTags().toArray(String[]::new),
                request.gender(), request.neutered(), request.introduction(), request.leashGreeting(),
                request.strangerResponse(), request.touchTolerance(), request.barkingLevel(), request.bitingLevel(),
                makeDefault, userId, dogId);
    }

    public int softDelete(long userId, long dogId) {
        return jdbcTemplate.update("""
                UPDATE dog_profile SET is_default = false, deleted_at = now(), updated_at = now()
                WHERE user_id = ? AND dog_id = ? AND deleted_at IS NULL
                """, userId, dogId);
    }

    public void clearDefault(long userId) {
        jdbcTemplate.update("UPDATE dog_profile SET is_default = false, updated_at = now() WHERE user_id = ? AND is_default = true", userId);
    }

    public void promoteFirst(long userId) {
        jdbcTemplate.update("""
                UPDATE dog_profile SET is_default = true, updated_at = now()
                WHERE dog_id = (
                    SELECT dog_id FROM dog_profile
                    WHERE user_id = ? AND deleted_at IS NULL
                    ORDER BY dog_id LIMIT 1
                )
                """, userId);
    }

    public boolean hasAny(long userId) {
        Boolean result = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM dog_profile WHERE user_id = ? AND deleted_at IS NULL)",
                Boolean.class, userId);
        return Boolean.TRUE.equals(result);
    }

    public void attachToWalk(long userId, long sessionId, List<Long> dogIds) {
        for (Long dogId : dogIds.stream().distinct().toList()) {
            Boolean owned = jdbcTemplate.queryForObject("""
                    SELECT EXISTS(
                        SELECT 1 FROM dog_profile
                        WHERE user_id = ? AND dog_id = ? AND deleted_at IS NULL
                    )
                    """, Boolean.class, userId, dogId);
            if (!Boolean.TRUE.equals(owned)) throw new IllegalArgumentException("invalid dog selection");
            jdbcTemplate.update("""
                    INSERT INTO walk_session_dog(session_id, dog_id, dog_name, breed)
                    SELECT ?, dog_id, name, breed
                    FROM dog_profile
                    WHERE user_id = ? AND dog_id = ? AND deleted_at IS NULL
                    ON CONFLICT DO NOTHING
                    """, sessionId, userId, dogId);
        }
    }

    private List<DogProfileResponse> query(String suffix, Object... args) {
        return jdbcTemplate.query("""
                SELECT dog_id, name, breed, birth_date, profile_image_url, temperament_tags,
                       gender, neutered, introduction, leash_greeting, stranger_response,
                       touch_tolerance, barking_level, biting_level,
                       is_default, created_at, updated_at
                FROM dog_profile
                """ + suffix, (rs, rowNumber) -> new DogProfileResponse(
                rs.getLong("dog_id"), rs.getString("name"), rs.getString("breed"),
                rs.getObject("birth_date", LocalDate.class), rs.getString("profile_image_url"),
                stringList(rs.getArray("temperament_tags")), rs.getString("gender"),
                rs.getObject("neutered", Boolean.class), rs.getString("introduction"),
                rs.getString("leash_greeting"), rs.getString("stranger_response"),
                rs.getString("touch_tolerance"), rs.getString("barking_level"), rs.getString("biting_level"),
                rs.getBoolean("is_default"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
        ), args);
    }

    private static List<String> stringList(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static String normalizeImage(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
