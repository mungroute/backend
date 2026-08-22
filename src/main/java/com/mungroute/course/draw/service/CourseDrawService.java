package com.mungroute.course.draw.service;

import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.draw.dto.ConnectCourseRequest;
import com.mungroute.course.draw.dto.ConnectCourseResponse;
import com.mungroute.course.draw.dto.CourseDrawMetricsResponse;
import com.mungroute.course.draw.dto.CustomCourseResponse;
import com.mungroute.course.draw.dto.DrawPointRequest;
import com.mungroute.course.draw.dto.GeoPointResponse;
import com.mungroute.course.draw.dto.SaveCustomCourseRequest;
import com.mungroute.course.draw.dto.SnapResponse;
import com.mungroute.course.draw.exception.CourseDrawErrorCode;
import com.mungroute.course.draw.repository.ConnectedWalkablePath;
import com.mungroute.course.draw.repository.CourseDrawRepository;
import com.mungroute.course.draw.repository.NetworkWaypoint;
import com.mungroute.course.draw.repository.SnappedWalkablePoint;
import com.mungroute.course.draw.repository.TraversedWalkableSegment;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.course.service.CourseProcessingException;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CourseDrawService {
    private static final double SNAP_RADIUS_M = 30.0;
    private static final double DIRECT_POINT_TOLERANCE_M = 3.0;
    private static final double MIN_BACKTRACK_SAVING_M = 5.0;
    private static final double MAX_COURSE_LENGTH_M = 50_000.0;

    private final AppUserRepository appUserRepository;
    private final CourseDrawRepository courseDrawRepository;
    private final CourseRoutingRepository courseRoutingRepository;
    private final CourseMetricsCalculator metricsCalculator;
    private final SolarPositionService solarPositionService;
    private final ObjectMapper objectMapper;

    public CourseDrawService(
            AppUserRepository appUserRepository,
            CourseDrawRepository courseDrawRepository,
            CourseRoutingRepository courseRoutingRepository,
            CourseMetricsCalculator metricsCalculator,
            SolarPositionService solarPositionService,
            ObjectMapper objectMapper
    ) {
        this.appUserRepository = appUserRepository;
        this.courseDrawRepository = courseDrawRepository;
        this.courseRoutingRepository = courseRoutingRepository;
        this.metricsCalculator = metricsCalculator;
        this.solarPositionService = solarPositionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SnapResponse snap(DrawPointRequest request) {
        SnappedWalkablePoint snapped = courseDrawRepository
                .snapToNearestWalkable(request.lat(), request.lon(), SNAP_RADIUS_M)
                .orElseThrow(() -> new BusinessException(CourseDrawErrorCode.NO_WALKABLE_LINK));
        boolean fallbackApplied = snapped.distanceM() > DIRECT_POINT_TOLERANCE_M;
        return new SnapResponse(
                fallbackApplied ? "FALLBACK_APPLIED" : "DIRECT",
                fallbackApplied,
                fallbackApplied ? "선택할 수 없는 위치라 가까운 산책로로 이동했어요." : null,
                new GeoPointResponse(request.lat(), request.lon()),
                new GeoPointResponse(snapped.lat(), snapped.lon()),
                snapped.distanceM(),
                snapped.nodeId(),
                snapped.segmentId()
        );
    }

    @Transactional(readOnly = true)
    public ConnectCourseResponse connect(ConnectCourseRequest request) {
        RouteAssembly route = buildRoute(request.waypoints(), true);
        CourseCalculationContext context = calculationContext(route.waypoints(), request.requestedAt());
        CourseMetrics metrics = calculateMetrics(route.traversedSegments(), context);
        return new ConnectCourseResponse(
                route.lastAddedSegmentIds(),
                route.segmentIds(),
                route.coordinates(),
                toResponse(metrics, context),
                route.ignoredWaypointIndexes()
        );
    }

    @Transactional
    public CustomCourseResponse save(long userId, SaveCustomCourseRequest request) {
        appUserRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CourseDrawErrorCode.USER_NOT_FOUND));
        RouteAssembly route = buildRoute(request.waypoints(), false);
        CourseCalculationContext context = calculationContext(request.waypoints(), request.requestedAt());
        CourseMetrics metrics = calculateMetrics(route.traversedSegments(), context);
        validateLoop(request);
        if (metrics.lengthM().doubleValue() > MAX_COURSE_LENGTH_M) {
            throw new BusinessException(CourseDrawErrorCode.INVALID_COURSE_PATH);
        }
        if (request.representative()) {
            courseDrawRepository.clearRepresentativeCourses(userId);
        }
        long courseId = courseDrawRepository.saveCustomCourse(
                userId,
                request.courseName().trim(),
                serializeWaypoints(request),
                route.segmentIds(),
                route.traversedSegments().stream().map(TraversedWalkableSegment::lengthM).toList(),
                serializeRoute(route.coordinates()),
                metrics,
                context.referenceTime().time().getHour(),
                request.loop(),
                request.representative()
        );
        return new CustomCourseResponse(
                courseId,
                "custom",
                request.courseName().trim(),
                request.loop(),
                request.representative(),
                toResponse(metrics, context)
        );
    }

    private CourseMetrics calculateMetrics(
            List<TraversedWalkableSegment> traversedSegments,
            CourseCalculationContext context
    ) {
        List<Long> segmentIds = traversedSegments.stream()
                .map(TraversedWalkableSegment::segmentId)
                .toList();
        if (!courseDrawRepository.allSegmentsWalkable(segmentIds)) {
            throw new BusinessException(CourseDrawErrorCode.INVALID_COURSE_PATH);
        }
        List<CourseSegmentData> sourceSegments = courseRoutingRepository
                .findSegmentsInOrder(segmentIds, context.referenceTime());
        if (sourceSegments.size() != traversedSegments.size()) {
            throw new BusinessException(CourseDrawErrorCode.INVALID_COURSE_PATH);
        }
        List<CourseSegmentData> measuredSegments = new ArrayList<>(sourceSegments.size());
        for (int index = 0; index < sourceSegments.size(); index++) {
            CourseSegmentData source = sourceSegments.get(index);
            TraversedWalkableSegment traversal = traversedSegments.get(index);
            if (source == null || source.segmentId() != traversal.segmentId()) {
                throw new BusinessException(CourseDrawErrorCode.INVALID_COURSE_PATH);
            }
            measuredSegments.add(source.withLength(traversal.lengthM()));
        }
        return calculateMetricsFromSegments(measuredSegments, context);
    }

    private CourseMetrics calculateMetricsFromSegments(
            List<CourseSegmentData> segments,
            CourseCalculationContext context
    ) {
        try {
            return metricsCalculator.calculate(segments, context);
        } catch (CourseProcessingException exception) {
            throw new BusinessException(CourseDrawErrorCode.THERMAL_DATA_UNAVAILABLE);
        }
    }

    private void validateLoop(SaveCustomCourseRequest request) {
        if (request.loop() && !samePosition(
                request.waypoints().getFirst(),
                request.waypoints().getLast()
        )) {
            throw new BusinessException(CourseDrawErrorCode.INVALID_COURSE_PATH);
        }
    }

    private RouteAssembly buildRoute(
            List<com.mungroute.course.draw.dto.DrawWaypointRequest> waypoints,
            boolean suppressLatestFallbackBacktrack
    ) {
        Map<RouteLeg, ConnectedWalkablePath> pathCache = new LinkedHashMap<>();
        WaypointSelection selection = suppressLatestFallbackBacktrack
                ? suppressLatestFallbackBacktrack(waypoints, pathCache)
                : new WaypointSelection(waypoints, List.of());
        List<TraversedWalkableSegment> traversedSegments = new ArrayList<>();
        List<GeoPointResponse> coordinates = new ArrayList<>();
        List<Long> lastAddedSegmentIds = List.of();
        for (int index = 1; index < selection.waypoints().size(); index++) {
            var from = selection.waypoints().get(index - 1);
            var to = selection.waypoints().get(index);
            ConnectedWalkablePath path = findPath(from, to, pathCache)
                    .orElseThrow(() -> new BusinessException(CourseDrawErrorCode.NOT_CONNECTED));
            traversedSegments.addAll(path.traversedSegments());
            appendCoordinates(coordinates, path.coordinates());
            lastAddedSegmentIds = path.segmentIds();
        }
        if (traversedSegments.isEmpty() || coordinates.size() < 2) {
            throw new BusinessException(CourseDrawErrorCode.NOT_CONNECTED);
        }
        return new RouteAssembly(
                traversedSegments,
                coordinates,
                lastAddedSegmentIds,
                selection.waypoints(),
                selection.ignoredWaypointIndexes()
        );
    }

    private WaypointSelection suppressLatestFallbackBacktrack(
            List<com.mungroute.course.draw.dto.DrawWaypointRequest> waypoints,
            Map<RouteLeg, ConnectedWalkablePath> pathCache
    ) {
        if (waypoints.size() < 3) {
            return new WaypointSelection(waypoints, List.of());
        }
        int candidateIndex = waypoints.size() - 2;
        var candidate = waypoints.get(candidateIndex);
        if (!candidate.fallbackApplied()) {
            return new WaypointSelection(waypoints, List.of());
        }
        var previous = waypoints.get(candidateIndex - 1);
        var next = waypoints.get(candidateIndex + 1);
        var incoming = findPath(previous, candidate, pathCache);
        var outgoing = findPath(candidate, next, pathCache);
        var direct = findPath(previous, next, pathCache);
        if (incoming.isEmpty() || outgoing.isEmpty() || direct.isEmpty()) {
            return new WaypointSelection(waypoints, List.of());
        }
        Set<Long> incomingSegments = new HashSet<>(incoming.get().segmentIds());
        boolean repeatsSegment = outgoing.get().segmentIds().stream().anyMatch(incomingSegments::contains);
        double routedThroughCandidateM = pathLengthM(incoming.get()) + pathLengthM(outgoing.get());
        double directLengthM = pathLengthM(direct.get());
        if (!repeatsSegment || routedThroughCandidateM - directLengthM < MIN_BACKTRACK_SAVING_M) {
            return new WaypointSelection(waypoints, List.of());
        }
        List<com.mungroute.course.draw.dto.DrawWaypointRequest> retained = new ArrayList<>(waypoints);
        retained.remove(candidateIndex);
        return new WaypointSelection(retained, List.of(candidateIndex));
    }

    private java.util.Optional<ConnectedWalkablePath> findPath(
            com.mungroute.course.draw.dto.DrawWaypointRequest from,
            com.mungroute.course.draw.dto.DrawWaypointRequest to,
            Map<RouteLeg, ConnectedWalkablePath> pathCache
    ) {
        RouteLeg leg = new RouteLeg(from, to);
        ConnectedWalkablePath cached = pathCache.get(leg);
        if (cached != null) return java.util.Optional.of(cached);
        var path = courseDrawRepository.findShortestWalkablePath(
                networkWaypoint(from),
                networkWaypoint(to)
        );
        path.ifPresent(value -> pathCache.put(leg, value));
        return path;
    }

    private double pathLengthM(ConnectedWalkablePath path) {
        return path.traversedSegments().stream()
                .mapToDouble(segment -> segment.lengthM().doubleValue())
                .sum();
    }

    private NetworkWaypoint networkWaypoint(com.mungroute.course.draw.dto.DrawWaypointRequest waypoint) {
        return new NetworkWaypoint(
                waypoint.segmentId(),
                waypoint.snapped().lat(),
                waypoint.snapped().lon()
        );
    }

    private void appendCoordinates(List<GeoPointResponse> target, List<GeoPointResponse> added) {
        for (GeoPointResponse point : added) {
            if (target.isEmpty() || !sameCoordinate(target.getLast(), point)) {
                target.add(point);
            }
        }
    }

    private CourseCalculationContext calculationContext(
            List<com.mungroute.course.draw.dto.DrawWaypointRequest> waypoints,
            java.time.Instant requestedAt
    ) {
        double lat = waypoints.stream().mapToDouble(waypoint -> waypoint.snapped().lat()).average()
                .orElseThrow(() -> new BusinessException(CourseDrawErrorCode.INVALID_COURSE_PATH));
        double lon = waypoints.stream().mapToDouble(waypoint -> waypoint.snapped().lon()).average()
                .orElseThrow(() -> new BusinessException(CourseDrawErrorCode.INVALID_COURSE_PATH));
        return solarPositionService.resolve(requestedAt, lat, lon);
    }

    private String serializeWaypoints(SaveCustomCourseRequest request) {
        try {
            return objectMapper.writeValueAsString(request.waypoints());
        } catch (JacksonException exception) {
            throw new IllegalStateException("커스텀 코스 지점을 JSON으로 변환하지 못했습니다.", exception);
        }
    }

    private String serializeRoute(List<GeoPointResponse> coordinates) {
        try {
            Map<String, Object> geoJson = new LinkedHashMap<>();
            geoJson.put("type", "LineString");
            geoJson.put("coordinates", coordinates.stream()
                    .map(point -> List.of(point.lon(), point.lat()))
                    .toList());
            return objectMapper.writeValueAsString(geoJson);
        } catch (JacksonException exception) {
            throw new IllegalStateException("커스텀 코스 경로를 GeoJSON으로 변환하지 못했습니다.", exception);
        }
    }

    private CourseDrawMetricsResponse toResponse(
            CourseMetrics metrics,
            CourseCalculationContext context
    ) {
        return new CourseDrawMetricsResponse(
                metrics.lengthM(),
                metrics.durationMin(),
                context.shadeApplicable() ? metrics.shadeRatio() : null,
                metrics.estimatedSurfaceTempC(),
                context.referenceTime().time().getHour(),
                weatherSource(metrics, context),
                metrics.basisDate(),
                metrics.confidence(),
                context.calculatedAt(),
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

    private boolean samePosition(
            com.mungroute.course.draw.dto.DrawWaypointRequest left,
            com.mungroute.course.draw.dto.DrawWaypointRequest right
    ) {
        return Math.abs(left.snapped().lat() - right.snapped().lat()) < 1e-7
                && Math.abs(left.snapped().lon() - right.snapped().lon()) < 1e-7;
    }

    private boolean sameCoordinate(GeoPointResponse left, GeoPointResponse right) {
        return Math.abs(left.lat() - right.lat()) < 1e-9
                && Math.abs(left.lon() - right.lon()) < 1e-9;
    }

    private record RouteAssembly(
            List<TraversedWalkableSegment> traversedSegments,
            List<GeoPointResponse> coordinates,
            List<Long> lastAddedSegmentIds,
            List<com.mungroute.course.draw.dto.DrawWaypointRequest> waypoints,
            List<Integer> ignoredWaypointIndexes
    ) {
        private RouteAssembly {
            traversedSegments = List.copyOf(traversedSegments);
            coordinates = List.copyOf(coordinates);
            lastAddedSegmentIds = List.copyOf(lastAddedSegmentIds);
            waypoints = List.copyOf(waypoints);
            ignoredWaypointIndexes = List.copyOf(ignoredWaypointIndexes);
        }

        private List<Long> segmentIds() {
            return traversedSegments.stream().map(TraversedWalkableSegment::segmentId).toList();
        }
    }

    private record WaypointSelection(
            List<com.mungroute.course.draw.dto.DrawWaypointRequest> waypoints,
            List<Integer> ignoredWaypointIndexes
    ) {
        private WaypointSelection {
            waypoints = List.copyOf(waypoints);
            ignoredWaypointIndexes = List.copyOf(ignoredWaypointIndexes);
        }
    }

    private record RouteLeg(
            com.mungroute.course.draw.dto.DrawWaypointRequest from,
            com.mungroute.course.draw.dto.DrawWaypointRequest to
    ) {
    }
}
