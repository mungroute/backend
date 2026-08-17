ALTER TABLE walk_session
    ADD COLUMN paused_at TIMESTAMPTZ,
    ADD COLUMN paused_duration_sec INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN match_status VARCHAR(24) NOT NULL DEFAULT 'NOT_PERFORMED',
    ADD COLUMN match_failure_reason VARCHAR(50),
    ADD COLUMN matched_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_walk_session_paused_duration
        CHECK (paused_duration_sec >= 0),
    ADD CONSTRAINT chk_walk_session_match_status
        CHECK (match_status IN (
            'NOT_PERFORMED',
            'INSUFFICIENT_POINTS',
            'MATCHED',
            'PARTIAL',
            'FAILED'
        ));

UPDATE walk_session
SET match_status = 'MATCHED',
    matched_at = COALESCE(ended_at, now())
WHERE cardinality(matched_segments) > 0;

CREATE INDEX idx_walk_session_saved_history
    ON walk_session(user_id, ended_at DESC)
    WHERE is_saved = true;

COMMENT ON COLUMN walk_session.paused_duration_sec IS
    '산책 일시정지 누적 시간(초). 종료 duration_sec에서 제외한다.';

COMMENT ON COLUMN walk_session.match_status IS
    'D6 GPS 맵매칭 결과. 종료 API 멱등성과 기록 상세 조회에 사용한다.';
