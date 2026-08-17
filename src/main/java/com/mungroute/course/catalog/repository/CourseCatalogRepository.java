package com.mungroute.course.catalog.repository;

import com.mungroute.course.domain.CourseSource;

import java.util.List;
import java.util.Optional;

public interface CourseCatalogRepository {
    List<CourseCatalogRow> findByUser(long userId, CourseSource source, int page, int size);

    Optional<CourseCatalogRow> findOwned(long userId, CourseSource source, long courseId);

    void clearRepresentatives(long userId);

    int setRepresentative(long userId, CourseSource source, long courseId, boolean representative);

    int deleteOwned(long userId, CourseSource source, long courseId);

    String routeGeoJson(List<Long> segmentIds);
}
