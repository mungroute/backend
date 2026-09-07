DELETE FROM route_segment
WHERE segment_id BETWEEN 8100000000000000 AND 8299999999999999;

DELETE FROM route_vertex
WHERE vertex_id BETWEEN 8000000000000000 AND 8000000000009999;

INSERT INTO route_vertex(vertex_id, geom, x, y)
SELECT 8000000000000000 + row_number * 8 + column_number,
       ST_SetSRID(ST_MakePoint(198000 + column_number * 100, 451000 + row_number * 100), 5186),
       198000 + column_number * 100,
       451000 + row_number * 100
FROM generate_series(0, 7) AS row_number
CROSS JOIN generate_series(0, 7) AS column_number;

WITH edges AS (
    SELECT row_number * 7 + column_number AS edge_number,
           8000000000000000 + row_number * 8 + column_number AS source,
           8000000000000000 + row_number * 8 + column_number + 1 AS target,
           ST_SetSRID(ST_MakeLine(
               ST_MakePoint(198000 + column_number * 100, 451000 + row_number * 100),
               ST_MakePoint(198000 + (column_number + 1) * 100, 451000 + row_number * 100)
           ), 5186) AS geom
    FROM generate_series(0, 7) AS row_number
    CROSS JOIN generate_series(0, 6) AS column_number

    UNION ALL

    SELECT 56 + row_number * 8 + column_number AS edge_number,
           8000000000000000 + row_number * 8 + column_number AS source,
           8000000000000000 + (row_number + 1) * 8 + column_number AS target,
           ST_SetSRID(ST_MakeLine(
               ST_MakePoint(198000 + column_number * 100, 451000 + row_number * 100),
               ST_MakePoint(198000 + column_number * 100, 451000 + (row_number + 1) * 100)
           ), 5186) AS geom
    FROM generate_series(0, 6) AS row_number
    CROSS JOIN generate_series(0, 7) AS column_number
)
INSERT INTO route_segment(
    segment_id, source, target, geom, length_m, surface_type,
    shade_ratio_09, shade_ratio_12, shade_ratio_15, shade_ratio_18,
    surface_temp_09_c, surface_temp_12_c, surface_temp_15_c,
    surface_temp_18_c, surface_temp_peak_c,
    thermal_model_confidence, thermal_weather_date, thermal_updated_at
)
SELECT 8100000000000000 + edge_number,
       source,
       target,
       geom,
       100.00,
       'asphalt',
       0.900,
       0.900,
       0.900,
       0.900,
       29.83,
       49.44,
       55.55,
       33.85,
       55.55,
       'LOW',
       DATE '2026-08-11',
       TIMESTAMPTZ '2026-08-11 12:00:00+09'
FROM edges;

WITH edges AS (
    SELECT row_number * 7 + column_number AS edge_number,
           198000 + column_number * 100 AS source_x,
           451000 + row_number * 100 AS source_y,
           198000 + (column_number + 1) * 100 AS target_x,
           451000 + row_number * 100 AS target_y,
           198050 + column_number * 100 AS bypass_x,
           451010 + row_number * 100 AS bypass_y
    FROM generate_series(0, 7) AS row_number
    CROSS JOIN generate_series(0, 6) AS column_number

    UNION ALL

    SELECT 56 + row_number * 8 + column_number AS edge_number,
           198000 + column_number * 100 AS source_x,
           451000 + row_number * 100 AS source_y,
           198000 + column_number * 100 AS target_x,
           451000 + (row_number + 1) * 100 AS target_y,
           198010 + column_number * 100 AS bypass_x,
           451050 + row_number * 100 AS bypass_y
    FROM generate_series(0, 6) AS row_number
    CROSS JOIN generate_series(0, 7) AS column_number
)
INSERT INTO route_vertex(vertex_id, geom, x, y)
SELECT 8000000000001000 + edge_number,
       ST_SetSRID(ST_MakePoint(bypass_x, bypass_y), 5186),
       bypass_x,
       bypass_y
FROM edges;

WITH edges AS (
    SELECT row_number * 7 + column_number AS edge_number,
           8000000000000000 + row_number * 8 + column_number AS source,
           8000000000000000 + row_number * 8 + column_number + 1 AS target,
           198000 + column_number * 100 AS source_x,
           451000 + row_number * 100 AS source_y,
           198000 + (column_number + 1) * 100 AS target_x,
           451000 + row_number * 100 AS target_y,
           198050 + column_number * 100 AS bypass_x,
           451010 + row_number * 100 AS bypass_y
    FROM generate_series(0, 7) AS row_number
    CROSS JOIN generate_series(0, 6) AS column_number

    UNION ALL

    SELECT 56 + row_number * 8 + column_number AS edge_number,
           8000000000000000 + row_number * 8 + column_number AS source,
           8000000000000000 + (row_number + 1) * 8 + column_number AS target,
           198000 + column_number * 100 AS source_x,
           451000 + row_number * 100 AS source_y,
           198000 + column_number * 100 AS target_x,
           451000 + (row_number + 1) * 100 AS target_y,
           198010 + column_number * 100 AS bypass_x,
           451050 + row_number * 100 AS bypass_y
    FROM generate_series(0, 6) AS row_number
    CROSS JOIN generate_series(0, 7) AS column_number
), bypass_legs AS (
    SELECT edge_number * 2 AS leg_number,
           source,
           8000000000001000 + edge_number AS target,
           ST_SetSRID(ST_MakeLine(
               ST_MakePoint(source_x, source_y),
               ST_MakePoint(bypass_x, bypass_y)
           ), 5186) AS geom
    FROM edges

    UNION ALL

    SELECT edge_number * 2 + 1 AS leg_number,
           8000000000001000 + edge_number AS source,
           target,
           ST_SetSRID(ST_MakeLine(
               ST_MakePoint(bypass_x, bypass_y),
               ST_MakePoint(target_x, target_y)
           ), 5186) AS geom
    FROM edges
)
INSERT INTO route_segment(
    segment_id, source, target, geom, length_m, surface_type,
    shade_ratio_09, shade_ratio_12, shade_ratio_15, shade_ratio_18,
    surface_temp_09_c, surface_temp_12_c, surface_temp_15_c,
    surface_temp_18_c, surface_temp_peak_c,
    thermal_model_confidence, thermal_weather_date, thermal_updated_at
)
SELECT 8200000000000000 + leg_number,
       source,
       target,
       geom,
       65.00,
       'asphalt',
       0.800,
       0.800,
       0.800,
       0.800,
       20.00,
       25.00,
       30.00,
       22.00,
       30.00,
       'HIGH',
       DATE '2026-08-11',
       TIMESTAMPTZ '2026-08-11 12:00:00+09'
FROM bypass_legs;
