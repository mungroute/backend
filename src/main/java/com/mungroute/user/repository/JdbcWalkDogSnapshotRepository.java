package com.mungroute.user.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class JdbcWalkDogSnapshotRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWalkDogSnapshotRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean attachToWalk(long userId, long sessionId, List<Long> dogIds) {
        if (dogIds.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        List<Long> distinctDogIds = dogIds.stream().distinct().toList();
        if (distinctDogIds.isEmpty()) return true;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("sessionId", sessionId)
                .addValue("dogIds", distinctDogIds);
        Integer ownedDogCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dog_profile dog
                JOIN walk_session walk
                  ON walk.session_id = :sessionId
                 AND walk.user_id = :userId
                WHERE dog.user_id = :userId
                  AND dog.dog_id IN (:dogIds)
                  AND dog.deleted_at IS NULL
                """, parameters, Integer.class);
        if (ownedDogCount == null || ownedDogCount != distinctDogIds.size()) {
            return false;
        }

        jdbcTemplate.update("""
                INSERT INTO walk_session_dog(session_id, dog_id, dog_name, breed)
                SELECT :sessionId, dog.dog_id, dog.name, dog.breed
                FROM dog_profile dog
                JOIN walk_session walk
                  ON walk.session_id = :sessionId
                 AND walk.user_id = :userId
                WHERE dog.user_id = :userId
                  AND dog.dog_id IN (:dogIds)
                  AND dog.deleted_at IS NULL
                ON CONFLICT DO NOTHING
                """, parameters);
        return true;
    }
}
