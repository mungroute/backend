package com.mungroute.thermal.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class JdbcRouteThermalRepository implements RouteThermalRepository {
    private static final String FIND_BY_SEGMENT_ID = """
            SELECT segment_id,
                   surface_temp_09_c,
                   surface_temp_12_c,
                   surface_temp_15_c,
                   surface_temp_18_c,
                   surface_temp_peak_c,
                   thermal_model_confidence,
                   thermal_weather_date,
                   thermal_updated_at
            FROM route_segment
            WHERE segment_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcRouteThermalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RouteThermalSnapshot> findBySegmentId(Long segmentId) {
        return jdbcTemplate.query(
                        FIND_BY_SEGMENT_ID,
                        (resultSet, rowNumber) -> new RouteThermalSnapshot(
                                resultSet.getLong("segment_id"),
                                resultSet.getBigDecimal("surface_temp_09_c"),
                                resultSet.getBigDecimal("surface_temp_12_c"),
                                resultSet.getBigDecimal("surface_temp_15_c"),
                                resultSet.getBigDecimal("surface_temp_18_c"),
                                resultSet.getBigDecimal("surface_temp_peak_c"),
                                resultSet.getString("thermal_model_confidence"),
                                resultSet.getObject("thermal_weather_date", LocalDate.class),
                                resultSet.getObject("thermal_updated_at", OffsetDateTime.class)
                        ),
                        segmentId
                ).stream()
                .findFirst();
    }
}
