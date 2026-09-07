package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.dto.CourseComparisonResponse;
import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.dto.CourseSummaryResponse;
import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Compatibility facade for the existing controller and cross-feature callers.
 * Query, comparison, representative mutation, and deletion each live in their
 * own use-case service behind this stable API.
 */
@Service
public class CourseCatalogService {
    private final CourseCatalogQueryService queryService;
    private final CourseComparisonService comparisonService;
    private final CourseRepresentativeService representativeService;
    private final CourseDeletionService deletionService;
    private final CourseCatalogMetricsAssembler assembler;

    public CourseCatalogService(
            CourseCatalogQueryService queryService,
            CourseComparisonService comparisonService,
            CourseRepresentativeService representativeService,
            CourseDeletionService deletionService,
            CourseCatalogMetricsAssembler assembler
    ) {
        this.queryService = queryService;
        this.comparisonService = comparisonService;
        this.representativeService = representativeService;
        this.deletionService = deletionService;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public List<CourseSummaryResponse> list(
            long userId,
            String sourceValue,
            int page,
            int size,
            Instant requestedAt
    ) {
        CourseSource source = sourceValue == null || sourceValue.isBlank()
                ? null
                : parseSource(sourceValue);
        return queryService.list(userId, source, page, size, requestedAt);
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse detail(
            long userId,
            String sourceValue,
            long courseId,
            Instant requestedAt
    ) {
        return queryService.detail(userId, parseSource(sourceValue), courseId, requestedAt);
    }

    @Transactional
    public CourseDetailResponse setRepresentative(
            long userId,
            String sourceValue,
            long courseId,
            boolean representative,
            Instant requestedAt
    ) {
        return assembler.detail(
                representativeService.setRepresentative(
                        userId,
                        parseSource(sourceValue),
                        courseId,
                        representative
                ),
                requestedAt
        );
    }

    @Transactional
    public void delete(long userId, String sourceValue, long courseId) {
        deletionService.delete(userId, parseSource(sourceValue), courseId);
    }

    @Transactional(readOnly = true)
    public CourseComparisonResponse comparison(
            long userId,
            String sourceValue,
            long courseId,
            Instant requestedAt
    ) {
        return comparisonService.comparison(
                userId,
                parseSource(sourceValue),
                courseId,
                requestedAt
        );
    }

    private CourseSource parseSource(String value) {
        try {
            CourseSource source = CourseSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (source == CourseSource.FIXTURE) throw new IllegalArgumentException();
            return source;
        } catch (RuntimeException exception) {
            throw new BusinessException(CourseCatalogErrorCode.INVALID_COURSE_SOURCE);
        }
    }
}
