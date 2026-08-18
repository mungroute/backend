ALTER TABLE meet_request_audit
    ALTER COLUMN actor_user_id DROP NOT NULL;

COMMENT ON COLUMN meet_request_audit.actor_user_id IS
    '사용자 상태 전이는 사용자 ID, 서버 만료 처리는 NULL. 좌표는 저장하지 않는다.';
