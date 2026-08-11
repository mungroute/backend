package com.mungroute.global.common.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> databaseInfo =
                jdbcTemplate.queryForMap("""
                        SELECT
                            current_database() AS database,
                            postgis_version() AS postgis,
                            pgr_version() AS pgrouting
                        """);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("status", "UP");
        response.put("checkedAt", OffsetDateTime.now());
        response.put("database", databaseInfo.get("database"));
        response.put("postgis", databaseInfo.get("postgis"));
        response.put("pgrouting", databaseInfo.get("pgrouting"));

        return response;
    }
}
