package com.mungroute.course.catalog.adapter;

import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.service.CourseCatalogService;
import com.mungroute.group.port.GroupCourseCatalogPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GroupCourseCatalogAdapter implements GroupCourseCatalogPort {
    private final CourseCatalogService courseCatalogService;

    public GroupCourseCatalogAdapter(CourseCatalogService courseCatalogService) {
        this.courseCatalogService = courseCatalogService;
    }

    @Override
    public CourseDetailResponse detail(long userId, String source, long courseId, Instant requestedAt) {
        return courseCatalogService.detail(userId, source, courseId, requestedAt);
    }
}
