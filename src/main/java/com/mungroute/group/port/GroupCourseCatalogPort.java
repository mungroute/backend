package com.mungroute.group.port;

import com.mungroute.course.catalog.dto.CourseDetailResponse;

import java.time.Instant;

/** Course details required by group sharing use cases. */
public interface GroupCourseCatalogPort {
    CourseDetailResponse detail(long userId, String source, long courseId, Instant requestedAt);
}
