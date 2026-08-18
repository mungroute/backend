ALTER TABLE walk_group
    ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN join_policy VARCHAR(16) NOT NULL DEFAULT 'INVITE_ONLY';

ALTER TABLE walk_group
    ADD CONSTRAINT chk_walk_group_visibility
        CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    ADD CONSTRAINT chk_walk_group_join_policy
        CHECK (join_policy IN ('OPEN', 'INVITE_ONLY')),
    ADD CONSTRAINT chk_private_group_invite_only
        CHECK (visibility = 'PUBLIC' OR join_policy = 'INVITE_ONLY');

CREATE INDEX idx_walk_group_public_discovery
    ON walk_group(created_at DESC, group_id DESC)
    WHERE deleted_at IS NULL AND visibility = 'PUBLIC';

COMMENT ON COLUMN walk_group.visibility IS 'PUBLIC은 탐색 가능, PRIVATE은 초대 코드로만 노출';
COMMENT ON COLUMN walk_group.join_policy IS 'OPEN은 공개 그룹 바로 참여, INVITE_ONLY는 초대 코드 필요';
