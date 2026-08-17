ALTER TABLE walk_session
    ADD COLUMN locked_mode VARCHAR(10);

UPDATE walk_session
SET locked_mode = mode
WHERE mode IN ('distance', 'meet');

ALTER TABLE walk_session
    ADD CONSTRAINT chk_walk_session_locked_mode
        CHECK (locked_mode IS NULL OR locked_mode IN ('distance', 'meet')),
    ADD CONSTRAINT chk_walk_session_mode_transition
        CHECK (
            (locked_mode IS NULL AND mode = 'off')
            OR
            (locked_mode IS NOT NULL AND (mode = 'off' OR mode = locked_mode))
        );

COMMENT ON COLUMN walk_session.locked_mode IS
    '산책 시작 시 선택한 주변 사용자 모드. 산책 중에는 이 모드와 off 사이에서만 전환할 수 있다.';
