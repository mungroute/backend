-- 사용자별 활성 산책을 하나만 허용하는 인덱스 추가
-- 이유 : 한 명이 동시에 여러 산책을 할 수 없기 때문에 제한
CREATE UNIQUE INDEX uk_walk_session_one_active_per_user
    ON walk_session(user_id)
    WHERE ended_at IS NULL;