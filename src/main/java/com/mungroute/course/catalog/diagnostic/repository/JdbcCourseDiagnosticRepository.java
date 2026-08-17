package com.mungroute.course.catalog.diagnostic.repository;

import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcCourseDiagnosticRepository implements CourseDiagnosticRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseDiagnosticRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CourseDiagnosticSegmentRow> findSegmentsInOrder(
            List<Long> segmentIds,
            ThermalReferenceTime referenceTime
    ) {
        if (segmentIds == null || segmentIds.isEmpty()) return List.of();
        String temperature = temperatureColumn(referenceTime);
        String shade = shadeColumn(referenceTime);
        String treeShade = treeShadeColumn(referenceTime);
        String buildingShade = buildingShadeColumn(referenceTime);
        String sql = """
                WITH input AS (
                    SELECT segment_id, ordinality::integer AS sequence
                    FROM unnest(?) WITH ORDINALITY AS item(segment_id, ordinality)
                )
                SELECT input.sequence,
                       segment.segment_id,
                       segment.length_m,
                       ST_AsGeoJSON(ST_Transform(segment.geom, 4326)) AS route_geo_json,
                       segment.%s AS surface_temp_c,
                       segment.%s AS shade_ratio,
                       segment.%s AS tree_shade_ratio,
                       segment.%s AS building_shade_ratio,
                       segment.surface_type,
                       segment.svf,
                       segment.albedo,
                       segment.park_proximity_m,
                       segment.thermal_model_confidence,
                       segment.thermal_weather_date
                FROM input
                JOIN route_segment segment ON segment.segment_id = input.segment_id
                ORDER BY input.sequence
                """.formatted(temperature, shade, treeShade, buildingShade);
        return jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setArray(1, connection.createArrayOf("bigint", segmentIds.toArray(Long[]::new)));
            return statement;
        }, this::mapSegment);
    }

    @Override
    public CourseDiagnosticBaseline findBaseline(ThermalReferenceTime referenceTime) {
        String temperature = temperatureColumn(referenceTime);
        String shade = shadeColumn(referenceTime);
        String sql = """
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY %s) AS median_temperature,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY %s) AS median_shade,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY svf) AS median_svf,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY albedo) AS median_albedo,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY park_proximity_m) AS median_park,
                       regr_slope(%s::double precision, %s::double precision) AS shade_slope,
                       regr_slope(%s::double precision, svf::double precision) AS svf_slope,
                       regr_slope(%s::double precision, albedo::double precision) AS albedo_slope,
                       regr_slope(%s::double precision, park_proximity_m::double precision) AS park_slope
                FROM route_segment
                WHERE %s IS NOT NULL
                """.formatted(
                temperature, shade, temperature, shade,
                temperature, temperature, temperature, temperature
        );
        BaselineValues values = jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> new BaselineValues(
                number(resultSet, "median_temperature"),
                number(resultSet, "median_shade"),
                number(resultSet, "median_svf"),
                number(resultSet, "median_albedo"),
                number(resultSet, "median_park"),
                number(resultSet, "shade_slope"),
                number(resultSet, "svf_slope"),
                number(resultSet, "albedo_slope"),
                number(resultSet, "park_slope")
        ));
        Map<String, Double> surfaceMedians = new HashMap<>();
        jdbcTemplate.query("""
                SELECT surface_type,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY %s) AS median_temperature
                FROM route_segment
                WHERE %s IS NOT NULL AND surface_type IS NOT NULL
                GROUP BY surface_type
                """.formatted(temperature, temperature), (RowCallbackHandler) resultSet -> surfaceMedians.put(
                resultSet.getString("surface_type"),
                number(resultSet, "median_temperature")
        ));
        if (values == null) values = BaselineValues.EMPTY;
        return new CourseDiagnosticBaseline(
                values.medianTemperature(), values.medianShade(), values.medianSvf(),
                values.medianAlbedo(), values.medianPark(), values.shadeSlope(),
                values.svfSlope(), values.albedoSlope(), values.parkSlope(), surfaceMedians
        );
    }

    private CourseDiagnosticSegmentRow mapSegment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CourseDiagnosticSegmentRow(
                resultSet.getInt("sequence"),
                resultSet.getLong("segment_id"),
                resultSet.getBigDecimal("length_m"),
                resultSet.getString("route_geo_json"),
                resultSet.getBigDecimal("surface_temp_c"),
                resultSet.getBigDecimal("shade_ratio"),
                resultSet.getBigDecimal("tree_shade_ratio"),
                resultSet.getBigDecimal("building_shade_ratio"),
                resultSet.getString("surface_type"),
                resultSet.getBigDecimal("svf"),
                resultSet.getBigDecimal("albedo"),
                resultSet.getBigDecimal("park_proximity_m"),
                resultSet.getString("thermal_model_confidence"),
                resultSet.getObject("thermal_weather_date", java.time.LocalDate.class)
        );
    }

    private static double number(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() || !Double.isFinite(value) ? 0.0 : value;
    }

    private static String temperatureColumn(ThermalReferenceTime referenceTime) {
        return switch (referenceTime) {
            case H09 -> "surface_temp_09_c";
            case H12 -> "surface_temp_12_c";
            case H15 -> "surface_temp_15_c";
            case H18 -> "surface_temp_18_c";
        };
    }

    private static String shadeColumn(ThermalReferenceTime referenceTime) {
        return switch (referenceTime) {
            case H09 -> "shade_ratio_09";
            case H12 -> "shade_ratio_12";
            case H15 -> "shade_ratio_15";
            case H18 -> "shade_ratio_18";
        };
    }

    private static String treeShadeColumn(ThermalReferenceTime referenceTime) {
        return switch (referenceTime) {
            case H09 -> "tree_shade_ratio_09";
            case H12 -> "tree_shade_ratio_12";
            case H15 -> "tree_shade_ratio_15";
            case H18 -> "tree_shade_ratio_18";
        };
    }

    private static String buildingShadeColumn(ThermalReferenceTime referenceTime) {
        return switch (referenceTime) {
            case H09 -> "bldg_shade_ratio_09";
            case H12 -> "bldg_shade_ratio_12";
            case H15 -> "bldg_shade_ratio_15";
            case H18 -> "bldg_shade_ratio_18";
        };
    }

    private record BaselineValues(
            double medianTemperature,
            double medianShade,
            double medianSvf,
            double medianAlbedo,
            double medianPark,
            double shadeSlope,
            double svfSlope,
            double albedoSlope,
            double parkSlope
    ) {
        private static final BaselineValues EMPTY = new BaselineValues(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
