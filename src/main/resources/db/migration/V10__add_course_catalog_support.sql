ALTER TABLE custom_course
    ADD COLUMN segment_lengths_m NUMERIC(10,3)[];

UPDATE custom_course course
SET segment_lengths_m = (
    SELECT ARRAY_AGG(segment.length_m::numeric(10,3) ORDER BY input.ordinality)
    FROM unnest(course.segment_ids) WITH ORDINALITY AS input(segment_id, ordinality)
    JOIN route_segment segment ON segment.segment_id = input.segment_id
);

ALTER TABLE custom_course
    ALTER COLUMN segment_lengths_m SET NOT NULL;

ALTER TABLE custom_course
    ADD CONSTRAINT chk_custom_course_segment_lengths CHECK (
        cardinality(segment_lengths_m) = cardinality(segment_ids)
        AND 0 < ALL(segment_lengths_m)
    );

DROP VIEW v_course;

CREATE VIEW v_course AS
SELECT
    'walk'::varchar(10) AS course_source,
    session_id AS course_id,
    user_id,
    course_name,
    distance_m::numeric(10,2) AS length_m,
    CEIL(duration_sec / 60.0)::integer AS duration_min,
    matched_segments AS segment_ids,
    NULL::numeric(10,3)[] AS segment_lengths_m,
    track_geom::geometry AS geom,
    is_loop,
    is_representative,
    started_at AS created_at
FROM walk_session
WHERE is_saved = true
  AND ended_at IS NOT NULL

UNION ALL

SELECT
    'custom'::varchar(10) AS course_source,
    custom_course_id AS course_id,
    user_id,
    course_name,
    length_m,
    duration_min,
    segment_ids,
    segment_lengths_m,
    geom,
    is_loop,
    is_representative,
    created_at
FROM custom_course;

COMMENT ON VIEW v_course IS
    '기록 산책과 직접 그린 코스를 source + id로 조회하고 커스텀 부분 링크 길이를 보존하는 통합 코스 뷰';
