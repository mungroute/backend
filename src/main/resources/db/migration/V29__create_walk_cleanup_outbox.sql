CREATE TABLE walk_cleanup_outbox (
    session_id          BIGINT PRIMARY KEY
        REFERENCES walk_session(session_id)
        ON DELETE CASCADE,
    user_id             BIGINT NOT NULL
        REFERENCES app_user(user_id),
    presence_completed  BOOLEAN NOT NULL DEFAULT false,
    meet_completed      BOOLEAN NOT NULL DEFAULT false,
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_until       TIMESTAMPTZ,
    last_error          VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT chk_walk_cleanup_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_walk_cleanup_completion
        CHECK (
            completed_at IS NULL
            OR (presence_completed = true AND meet_completed = true)
        )
);

CREATE INDEX idx_walk_cleanup_outbox_due
    ON walk_cleanup_outbox(next_attempt_at, session_id)
    WHERE completed_at IS NULL;

COMMENT ON TABLE walk_cleanup_outbox IS
    '산책 종료 DB 커밋 이후 Presence와 Meet 정리를 재시도하는 transactional outbox.';
