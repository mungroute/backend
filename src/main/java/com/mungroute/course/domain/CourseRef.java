package com.mungroute.course.domain;

import java.util.Objects;

public record CourseRef(CourseSource source, long id) {
    public CourseRef {
        Objects.requireNonNull(source, "source는 필수입니다.");
        if (id <= 0) {
            throw new IllegalArgumentException("course id는 1 이상이어야 합니다.");
        }
    }
}
