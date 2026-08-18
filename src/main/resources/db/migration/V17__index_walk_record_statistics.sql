CREATE INDEX idx_walk_session_saved_user_ended
    ON walk_session(user_id, ended_at DESC, session_id DESC)
    WHERE is_saved = true;

CREATE INDEX idx_walk_session_dog_dog_session
    ON walk_session_dog(dog_id, session_id);
