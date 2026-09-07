package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.dto.CourseSummaryResponse;
import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CourseCatalogQueryService {
    private final CourseCatalogRepository catalogRepository;
    private final CourseCatalogMetricsAssembler assembler;

    public CourseCatalogQueryService(
            CourseCatalogRepository catalogRepository,
            CourseCatalogMetricsAssembler assembler
    ) {
        this.catalogRepository = catalogRepository;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public List<CourseSummaryResponse> list(
            long userId,
            CourseSource source,
            int page,
            int size,
            Instant requestedAt
    ) {
        return catalogRepository.findByUser(userId, source, page, size).stream()
                .map(row -> assembler.summary(row, requestedAt))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse detail(
            long userId,
            CourseSource source,
            long courseId,
            Instant requestedAt
    ) {
        return assembler.detail(owned(userId, source, courseId), requestedAt);
    }

    CourseCatalogRow owned(long userId, CourseSource source, long courseId) {
        return catalogRepository.findOwned(userId, source, courseId)
                .orElseThrow(() -> new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND));
    }
}
