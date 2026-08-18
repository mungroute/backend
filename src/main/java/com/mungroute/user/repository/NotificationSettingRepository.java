package com.mungroute.user.repository;

import com.mungroute.user.dto.request.NotificationSettingRequest;
import com.mungroute.user.dto.response.NotificationSettingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationSettingRepository {
    private final JdbcTemplate jdbcTemplate;

    public NotificationSettingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NotificationSettingResponse getOrCreate(long userId) {
        jdbcTemplate.update("INSERT INTO notification_setting(user_id) VALUES (?) ON CONFLICT DO NOTHING", userId);
        return jdbcTemplate.queryForObject("""
                SELECT service_enabled, distance_enabled, meet_enabled, group_enabled
                FROM notification_setting WHERE user_id = ?
                """, (rs, rowNumber) -> new NotificationSettingResponse(
                rs.getBoolean("service_enabled"), rs.getBoolean("distance_enabled"),
                rs.getBoolean("meet_enabled"), rs.getBoolean("group_enabled")
        ), userId);
    }

    public NotificationSettingResponse update(long userId, NotificationSettingRequest request) {
        jdbcTemplate.update("""
                INSERT INTO notification_setting(user_id, service_enabled, distance_enabled, meet_enabled, group_enabled)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    service_enabled = EXCLUDED.service_enabled,
                    distance_enabled = EXCLUDED.distance_enabled,
                    meet_enabled = EXCLUDED.meet_enabled,
                    group_enabled = EXCLUDED.group_enabled,
                    updated_at = now()
                """, userId, request.serviceEnabled(), request.distanceEnabled(), request.meetEnabled(), request.groupEnabled());
        return getOrCreate(userId);
    }
}
