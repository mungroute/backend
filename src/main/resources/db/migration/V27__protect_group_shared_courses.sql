-- Remove references left by deletions made before shared-course protection existed.
DELETE FROM group_shared_course shared
WHERE (shared.course_source = 'custom' AND NOT EXISTS (
    SELECT 1 FROM custom_course course WHERE course.custom_course_id = shared.course_id
)) OR (shared.course_source = 'walk' AND NOT EXISTS (
    SELECT 1 FROM walk_session session WHERE session.session_id = shared.course_id
));

CREATE OR REPLACE FUNCTION prevent_group_shared_course_source_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM group_shared_course shared
        WHERE shared.course_source = TG_ARGV[0]
          AND shared.course_id = CASE
              WHEN TG_ARGV[0] = 'custom' THEN (to_jsonb(OLD) ->> 'custom_course_id')::bigint
              ELSE (to_jsonb(OLD) ->> 'session_id')::bigint
          END
    ) THEN
        RAISE EXCEPTION 'group-shared course cannot be deleted'
            USING ERRCODE = '23503';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER protect_group_shared_custom_course
BEFORE DELETE ON custom_course
FOR EACH ROW
EXECUTE FUNCTION prevent_group_shared_course_source_delete('custom');

CREATE TRIGGER protect_group_shared_walk_course
BEFORE DELETE ON walk_session
FOR EACH ROW
EXECUTE FUNCTION prevent_group_shared_course_source_delete('walk');
