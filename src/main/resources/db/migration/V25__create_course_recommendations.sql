CREATE TABLE course_recommendation_request (
    request_id            UUID PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    target_duration_min   SMALLINT NOT NULL,
    departure_at          TIMESTAMPTZ NOT NULL,
    start_geom            GEOMETRY(Point, 4326) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    response_payload      JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_course_recommendation_duration CHECK (target_duration_min BETWEEN 10 AND 60),
    CONSTRAINT chk_course_recommendation_status CHECK (status IN ('COMPLETED', 'FAILED'))
);

CREATE INDEX idx_course_recommendation_user_created
    ON course_recommendation_request(user_id, created_at DESC);

CREATE INDEX idx_course_recommendation_expires
    ON course_recommendation_request(expires_at);

CREATE INDEX idx_course_recommendation_start_geom
    ON course_recommendation_request USING GIST(start_geom);

COMMENT ON TABLE course_recommendation_request IS
    '시간 맞춤 추천 요청과 완성된 후보 응답. 생성 후보는 만료 전까지 재조회한다.';
