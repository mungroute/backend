UPDATE custom_course course
SET course_name = left(
    rtrim(regexp_replace(course.course_name, '([[:space:]]*[(]그룹[)])+$', '')) || ' (그룹)',
    100
)
WHERE EXISTS (
    SELECT 1
    FROM group_course_save saved
    WHERE saved.saved_course_id = course.custom_course_id
)
AND course.course_name ~ '([[:space:]]*[(]그룹[)]){2,}$';
