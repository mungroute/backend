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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CourseCatalogService {
    private static final double COMPARISON_DETOUR_RATIO = 0.15;

    private final CourseCatalogRepository catalogRepository;
    private final CourseRoutingRepository routingRepository;
    private final CourseMetricsCalculator metricsCalculator;
    private final SegmentSwapService segmentSwapService;
    private final SolarPositionService solarPositionService;
    private final ObjectMapper objectMapper;
    private final CourseRepresentativeService representativeService;
    private final CourseDeletionService deletionService;

    public CourseCatalogService(
            CourseCatalogRepository catalogRepository,
            CourseRoutingRepository routingRepository,
            CourseMetricsCalculator metricsCalculator,
            SegmentSwapService segmentSwapService,
            SolarPositionService solarPositionService,
            ObjectMapper objectMapper,
            CourseRepresentativeService representativeService,
            CourseDeletionService deletionService
    ) {
        this.catalogRepository = catalogRepository;
        this.routingRepository = routingRepository;
        this.metricsCalculator = metricsCalculator;
        this.segmentSwapService = segmentSwapService;
        this.solarPositionService = solarPositionService;
        this.objectMapper = objectMapper;
        this.representativeService = representativeService;
        this.deletionService = deletionService;
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
        return toDetail(representativeService.setRepresentative(
                userId, source, courseId, representative
        ), requestedAt);
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
                row.segmentLengthsM(),
                context,
                Math.max(1, usualMetrics.durationMin()),
                COMPARISON_DETOUR_RATIO
        );
        if (!result.hasAlternative()) {
            return unavailable(row, context, usualMetrics, usualRoute, result.reason().name());
        }
        PathEndpoints courseEndpoints;
        try {
            courseEndpoints = pathEndpoints(row.segmentIds(), context);
        } catch (CourseProcessingException exception) {
            return unavailable(row, context, usualMetrics, usualRoute, exception.reason().name());
        }
        JsonNode alternativeRoute = parseGeoJson(catalogRepository.routeGeoJson(
                courseEndpoints.startNode(),
                courseEndpoints.endNode(),
                result.alternativePath().segmentIds()
        ));
        if (!isContinuousLine(alternativeRoute)) {
            return unavailable(row, context, usualMetrics, usualRoute, "COURSE_NOT_CONNECTED");
        }
        List<SwappedSectionResponse> swappedSections = new ArrayList<>();
        for (var section : result.swappedSections()) {
            JsonNode originalSectionRoute = sectionRouteGeoJson(
                    section.startNode(),
                    section.endNode(),
                    section.originalSegmentIds()
            );
            JsonNode alternativeSectionRoute = parseGeoJson(catalogRepository.routeGeoJson(
                    section.startNode(),
                    section.endNode(),
                    section.alternativeSegmentIds()
            ));
            if (!isContinuousLine(originalSectionRoute) || !isContinuousLine(alternativeSectionRoute)) {
                return unavailable(row, context, usualMetrics, usualRoute, "COURSE_NOT_CONNECTED");
            }
            swappedSections.add(new SwappedSectionResponse(
                    section.sectionIndex(),
                    section.fromSegmentIndex(),
                    section.toSegmentIndexExclusive(),
                    section.originalSegmentIds(),
                    section.alternativeSegmentIds(),
                    originalSectionRoute,
                    alternativeSectionRoute,
                    section.temperatureImprovementC(),
                    section.addedLengthM()
            ));
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
                alternativeRoute,
                usual.estimatedSurfaceTempC().subtract(alternative.estimatedSurfaceTempC()),
                alternative.lengthM().subtract(usual.lengthM()),
                swappedSections,
                null
        );
    }

    private PathEndpoints pathEndpoints(List<Long> segmentIds, CourseCalculationContext context) {
        List<CourseSegmentData> segments = routingRepository.findSegmentsInOrder(
                segmentIds,
                context.referenceTime()
        );
        if (segments.size() != segmentIds.size() || segments.isEmpty()
                || segments.stream().anyMatch(segment -> segment == null)) {
            throw new CourseProcessingException(
                    com.mungroute.course.domain.AlternativeReason.COURSE_NOT_CONNECTED,
                    "코스 시작 노드를 확인할 수 없습니다."
            );
        }
        CourseSegmentData first = segments.getFirst();
        for (long startNode : List.of(first.source(), first.target())) {
            Long endNode = pathEnd(segments, 0, startNode, new HashSet<>());
            if (endNode != null) return new PathEndpoints(startNode, endNode);
        }
        throw new CourseProcessingException(
                com.mungroute.course.domain.AlternativeReason.COURSE_NOT_CONNECTED,
                "코스 구간이 서로 연결되어 있지 않습니다."
        );
    }

    private Long pathEnd(
            List<CourseSegmentData> segments,
            int index,
            long currentNode,
            Set<PathState> failed
    ) {
        if (index == segments.size()) return currentNode;
        PathState state = new PathState(index, currentNode);
        if (failed.contains(state)) return null;

        CourseSegmentData segment = segments.get(index);
        Long nextNode = otherNode(segment, currentNode);
        if (nextNode == null) {
            failed.add(state);
            return null;
        }

        Long endNode = pathEnd(segments, index + 1, nextNode, failed);
        if (endNode != null) return endNode;

        // A waypoint can split one route segment into two measured occurrences.
        // In that representation the duplicate pair describes one traversal, not
        // an out-and-back. Only use this interpretation when full traversal fails.
        if (isInternalDuplicatePair(segments, index)) {
            endNode = pathEnd(segments, index + 2, nextNode, failed);
            if (endNode != null) return endNode;
        }

        failed.add(state);
        return null;
    }

    private Long otherNode(CourseSegmentData segment, long node) {
        if (segment.source() == node) return segment.target();
        if (segment.target() == node) return segment.source();
        return null;
    }

    private boolean isInternalDuplicatePair(List<CourseSegmentData> segments, int index) {
        if (index == 0 || index + 2 >= segments.size()) return false;
        CourseSegmentData current = segments.get(index);
        return sameUndirectedSegment(current, segments.get(index + 1))
                && !sameUndirectedSegment(segments.get(index - 1), current)
                && !sameUndirectedSegment(current, segments.get(index + 2));
    }

    private boolean sameUndirectedSegment(CourseSegmentData left, CourseSegmentData right) {
        return left.segmentId() == right.segmentId()
                && (left.source() == right.source() && left.target() == right.target()
                || left.source() == right.target() && left.target() == right.source());
    }

    private JsonNode sectionRouteGeoJson(long startNode, long endNode, List<Long> segmentIds) {
        JsonNode route = parseGeoJson(catalogRepository.routeGeoJson(startNode, endNode, segmentIds));
        if (isContinuousLine(route)) return route;

        List<Long> collapsed = collapseConsecutiveDuplicates(segmentIds);
        if (collapsed.size() == segmentIds.size()) return route;
        return parseGeoJson(catalogRepository.routeGeoJson(startNode, endNode, collapsed));
    }

    private List<Long> collapseConsecutiveDuplicates(List<Long> segmentIds) {
        List<Long> collapsed = new ArrayList<>(segmentIds.size());
        for (Long segmentId : segmentIds) {
            if (collapsed.isEmpty() || !collapsed.getLast().equals(segmentId)) {
                collapsed.add(segmentId);
            }
        }
        return collapsed;
    }

    private boolean isContinuousLine(JsonNode route) {
        return route != null
                && "LineString".equals(route.path("type").asText())
                && route.path("coordinates").isArray()
                && route.path("coordinates").size() >= 2;
    }

    private record PathEndpoints(long startNode, long endNode) {
    }

    private record PathState(int segmentIndex, long currentNode) {
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
                measured.add(source.withLength(row.segmentLengthsM().get(index)));
            }
        }
        try {
            return metricsCalculator.calculate(measured, context);
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
                weatherSource(metrics, context),
                metrics.basisDate(), metrics.confidence(), context.calculatedAt(),
                context.solarState().name(),
                Math.round(context.solarElevationDeg() * 1000.0) / 1000.0,
                context.shadeApplicable()
        );
    }

    private static String weatherSource(CourseMetrics metrics, CourseCalculationContext context) {
        return switch (metrics.thermalStatus()) {
            case "NOWCAST", "CACHED", "OBSERVED", "FORECAST" -> metrics.thermalStatus();
            default -> context.shadeApplicable() ? "SCENARIO" : "SCENARIO_REFERENCE";
        };
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
