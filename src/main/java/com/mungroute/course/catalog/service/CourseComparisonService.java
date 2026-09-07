package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.dto.CourseComparisonResponse;
import com.mungroute.course.catalog.dto.CourseMetricResponse;
import com.mungroute.course.catalog.dto.SwappedSectionResponse;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CoursePath;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseProcessingException;
import com.mungroute.course.service.SegmentSwapService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CourseComparisonService {
    private static final double COMPARISON_DETOUR_RATIO = 0.15;

    private final CourseCatalogRepository catalogRepository;
    private final CourseRoutingRepository routingRepository;
    private final SegmentSwapService segmentSwapService;
    private final CourseCatalogMetricsAssembler assembler;
    private final CourseCatalogQueryService queryService;

    public CourseComparisonService(
            CourseCatalogRepository catalogRepository,
            CourseRoutingRepository routingRepository,
            SegmentSwapService segmentSwapService,
            CourseCatalogMetricsAssembler assembler,
            CourseCatalogQueryService queryService
    ) {
        this.catalogRepository = catalogRepository;
        this.routingRepository = routingRepository;
        this.segmentSwapService = segmentSwapService;
        this.assembler = assembler;
        this.queryService = queryService;
    }

    @Transactional(readOnly = true)
    public CourseComparisonResponse comparison(
            long userId,
            CourseSource source,
            long courseId,
            Instant requestedAt
    ) {
        CourseCatalogRow row = queryService.owned(userId, source, courseId);
        CourseCalculationContext context = assembler.context(row, requestedAt);
        CourseMetrics usualMetrics = assembler.calculate(row, context, true);
        JsonNode usualRoute = assembler.parseGeoJson(row.routeGeoJson());
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
        JsonNode alternativeRoute = assembler.parseGeoJson(catalogRepository.routeGeoJson(
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
            JsonNode alternativeSectionRoute = assembler.parseGeoJson(catalogRepository.routeGeoJson(
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
        CourseMetricResponse usual = assembler.metric(
                result.base() == null ? usualMetrics : result.base(),
                context
        );
        CourseMetricResponse alternative = assembler.metric(result.alternative(), context);
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
        JsonNode route = assembler.parseGeoJson(
                catalogRepository.routeGeoJson(startNode, endNode, segmentIds)
        );
        if (isContinuousLine(route)) return route;

        List<Long> collapsed = collapseConsecutiveDuplicates(segmentIds);
        if (collapsed.size() == segmentIds.size()) return route;
        return assembler.parseGeoJson(
                catalogRepository.routeGeoJson(startNode, endNode, collapsed)
        );
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

    private CourseComparisonResponse unavailable(
            CourseCatalogRow row,
            CourseCalculationContext context,
            CourseMetrics usualMetrics,
            JsonNode usualRoute,
            String reason
    ) {
        return new CourseComparisonResponse(
                row.source(), row.courseId(), row.courseName(), false,
                assembler.metric(usualMetrics, context), null,
                usualRoute, null, null, null, List.of(), reason
        );
    }

    private record PathEndpoints(long startNode, long endNode) {
    }

    private record PathState(int segmentIndex, long currentNode) {
    }
}
