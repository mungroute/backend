package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.dto.CourseComparisonResponse;
import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.dto.CourseMetricResponse;
import com.mungroute.course.catalog.dto.CourseSummaryResponse;
import com.mungroute.course.catalog.dto.SwappedSectionResponse;
import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CoursePath;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.course.service.CourseProcessingException;
import com.mungroute.course.service.SegmentSwapService;
import com.mungroute.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CourseCatalogService {
    private static final double COMPARISON_DETOUR_RATIO = 0.15;

    private final CourseCatalogRepository catalogRepository;
    private final CourseRoutingRepository routingRepository;
    private final CourseMetricsCalculator metricsCalculator;
    private final SegmentSwapService segmentSwapService;
    private final SolarPositionService solarPositionService;
    private final ObjectMapper objectMapper;

    public CourseCatalogService(
            CourseCatalogRepository catalogRepository,
            CourseRoutingRepository routingRepository,
            CourseMetricsCalculator metricsCalculator,
            SegmentSwapService segmentSwapService,
            SolarPositionService solarPositionService,
            ObjectMapper objectMapper
    ) {
        this.catalogRepository = catalogRepository;
        this.routingRepository = routingRepository;
        this.metricsCalculator = metricsCalculator;
        this.segmentSwapService = segmentSwapService;
        this.solarPositionService = solarPositionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CourseSummaryResponse> list(
            long userId,
            String sourceValue,
            int page,
            int size,
            Instant requestedAt
    ) {
        CourseSource source = sourceValue == null || sourceValue.isBlank() ? null : parseSource(sourceValue);
        return catalogRepository.findByUser(userId, source, page, size).stream()
                .map(row -> toSummary(row, requestedAt))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse detail(long userId, String sourceValue, long courseId, Instant requestedAt) {
        CourseCatalogRow row = owned(userId, parseSource(sourceValue), courseId);
        return toDetail(row, requestedAt);
    }

    @Transactional
    public CourseDetailResponse setRepresentative(
            long userId,
            String sourceValue,
            long courseId,
            boolean representative,
            Instant requestedAt
    ) {
        CourseSource source = parseSource(sourceValue);
        CourseCatalogRow row = owned(userId, source, courseId);
        if (representative && source == CourseSource.WALK && (!row.loop() || row.segmentIds().isEmpty())) {
            throw new BusinessException(CourseCatalogErrorCode.REPRESENTATIVE_COURSE_INELIGIBLE);
        }
        if (representative) {
            catalogRepository.clearRepresentatives(userId);
        }
        if (catalogRepository.setRepresentative(userId, source, courseId, representative) != 1) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND);
        }
        return toDetail(owned(userId, source, courseId), requestedAt);
    }

    @Transactional
    public void delete(long userId, String sourceValue, long courseId) {
        CourseSource source = parseSource(sourceValue);
        owned(userId, source, courseId);
        if (catalogRepository.deleteOwned(userId, source, courseId) != 1) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public CourseComparisonResponse comparison(
            long userId,
            String sourceValue,
            long courseId,
            Instant requestedAt
    ) {
        CourseSource source = parseSource(sourceValue);
        CourseCatalogRow row = owned(userId, source, courseId);
        CourseCalculationContext context = context(row, requestedAt);
        CourseMetrics usualMetrics = calculate(row, context, true);
        JsonNode usualRoute = parseGeoJson(row.routeGeoJson());
        if (row.segmentIds().size() < 3) {
            return unavailable(row, context, usualMetrics, usualRoute, "COURSE_NOT_CONNECTED");
        }
        var result = segmentSwapService.recommend(
                new CoursePath(row.segmentIds()),
                context.referenceTime(),
                Math.max(1, usualMetrics.durationMin()),
                COMPARISON_DETOUR_RATIO
        );
        if (!result.hasAlternative()) {
            return unavailable(row, context, usualMetrics, usualRoute, result.reason().name());
        }
        CourseMetricResponse usual = toMetric(result.base() == null ? usualMetrics : result.base(), context);
        CourseMetricResponse alternative = toMetric(result.alternative(), context);
        return new CourseComparisonResponse(
                row.source(),
                row.courseId(),
                row.courseName(),
                true,
                usual,
                alternative,
                usualRoute,
                parseGeoJson(catalogRepository.routeGeoJson(result.alternativePath().segmentIds())),
                usual.estimatedSurfaceTempC().subtract(alternative.estimatedSurfaceTempC()),
                alternative.lengthM().subtract(usual.lengthM()),
                result.swappedSections().stream().map(section -> new SwappedSectionResponse(
                        section.sectionIndex(),
                        section.originalSegmentIds(),
                        section.alternativeSegmentIds(),
                        section.temperatureImprovementC(),
                        section.addedLengthM()
                )).toList(),
                null
        );
    }

    private CourseComparisonResponse unavailable(
            CourseCatalogRow row,
            CourseCalculationContext context,
            CourseMetrics usualMetrics,
            JsonNode usualRoute,
            String reason
    ) {
        return new CourseComparisonResponse(
                row.source(), row.courseId(), row.courseName(), false,
                toMetric(usualMetrics, context), null,
                usualRoute, null, null, null, List.of(), reason
        );
    }

    private CourseSummaryResponse toSummary(CourseCatalogRow row, Instant requestedAt) {
        CourseMetricResponse metrics = nullableMetrics(row, requestedAt);
        return new CourseSummaryResponse(
                row.source(), row.courseId(), row.courseName(),
                metrics == null ? row.storedLengthM() : metrics.lengthM(),
                metrics == null ? row.storedDurationMin() : metrics.durationMin(),
                row.loop(), row.representative(), row.createdAt(), metrics
        );
    }

    private CourseDetailResponse toDetail(CourseCatalogRow row, Instant requestedAt) {
        return new CourseDetailResponse(
                row.source(), row.courseId(), row.courseName(), row.loop(), row.representative(),
                row.createdAt(), row.segmentIds(), parseGeoJson(row.routeGeoJson()), nullableMetrics(row, requestedAt)
        );
    }

    private CourseMetricResponse nullableMetrics(CourseCatalogRow row, Instant requestedAt) {
        if (row.segmentIds().isEmpty()) return null;
        CourseCalculationContext context = context(row, requestedAt);
        try {
            return toMetric(calculate(row, context, false), context);
        } catch (CourseProcessingException | BusinessException exception) {
            return null;
        }
    }

    private CourseMetrics calculate(CourseCatalogRow row, CourseCalculationContext context, boolean required) {
        List<CourseSegmentData> sourceSegments = routingRepository
                .findSegmentsInOrder(row.segmentIds(), context.referenceTime());
        if (sourceSegments.size() != row.segmentIds().size() || sourceSegments.stream().anyMatch(value -> value == null)) {
            if (required) throw new BusinessException(CourseCatalogErrorCode.COURSE_METRICS_UNAVAILABLE);
            throw new CourseProcessingException(
                    com.mungroute.course.domain.AlternativeReason.THERMAL_DATA_UNAVAILABLE,
                    "코스 구간 지표가 없습니다."
            );
        }
        List<CourseSegmentData> measured = sourceSegments;
        if (row.segmentLengthsM().size() == sourceSegments.size()) {
            measured = new ArrayList<>(sourceSegments.size());
            for (int index = 0; index < sourceSegments.size(); index++) {
                CourseSegmentData source = sourceSegments.get(index);
                measured.add(new CourseSegmentData(
                        source.segmentId(), source.source(), source.target(), row.segmentLengthsM().get(index),
                        source.shadeRatio(), source.surfaceTempC(), source.thermalModelConfidence(), source.thermalWeatherDate()
                ));
            }
        }
        try {
            return metricsCalculator.calculate(measured);
        } catch (CourseProcessingException exception) {
            if (required) throw new BusinessException(CourseCatalogErrorCode.COURSE_METRICS_UNAVAILABLE);
            throw exception;
        }
    }

    private CourseCalculationContext context(CourseCatalogRow row, Instant requestedAt) {
        return solarPositionService.resolve(requestedAt, row.centerLat(), row.centerLon());
    }

    private CourseMetricResponse toMetric(CourseMetrics metrics, CourseCalculationContext context) {
        return new CourseMetricResponse(
                metrics.lengthM(), metrics.durationMin(),
                context.shadeApplicable() ? metrics.shadeRatio() : null,
                metrics.estimatedSurfaceTempC(), context.referenceTime().time().getHour(),
                context.shadeApplicable() ? "SCENARIO" : "SCENARIO_REFERENCE",
                metrics.basisDate(), metrics.confidence(), context.calculatedAt(),
                context.solarState().name(),
                Math.round(context.solarElevationDeg() * 1000.0) / 1000.0,
                context.shadeApplicable()
        );
    }

    private CourseCatalogRow owned(long userId, CourseSource source, long courseId) {
        return catalogRepository.findOwned(userId, source, courseId)
                .orElseThrow(() -> new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND));
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

    private JsonNode parseGeoJson(String geoJson) {
        if (geoJson == null) return null;
        try {
            return objectMapper.readTree(geoJson);
        } catch (JacksonException exception) {
            throw new IllegalStateException("DB의 코스 GeoJSON을 해석할 수 없습니다.", exception);
        }
    }
}
