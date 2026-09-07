package com.mungroute.course.catalog.port;

import com.mungroute.course.domain.CourseSource;

public interface CourseSharingReferencePort {
    boolean isShared(CourseSource source, long courseId);
}
