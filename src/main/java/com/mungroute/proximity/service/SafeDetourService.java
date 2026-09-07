package com.mungroute.proximity.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.proximity.detour.SafeDetourPath;
import com.mungroute.proximity.detour.SafeDetourRouteRepository;
import com.mungroute.proximity.dto.request.SafeDetourPointRequest;
import com.mungroute.proximity.dto.request.SafeDetourRequest;
import com.mungroute.proximity.dto.response.SafeDetourPointResponse;
import com.mungroute.proximity.dto.response.SafeDetourResponse;
import com.mungroute.proximity.exception.PresenceErrorCode;
import com.mungroute.proximity.port.SafeDetourSnapPort;
import com.mungroute.proximity.store.NearbyPresenceLocation;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.port.WalkSessionAccessPort;
import com.mungroute.walk.port.WalkSessionSnapshot;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SafeDetourService {
    private static final double REJOIN_DISTANCE_M = 180;
    private static final double MIN_REJOIN_DISTANCE_M = 80;
    private static final double MAX_ADDED_DISTANCE_M = 200;
    private static final double MAX_DETOUR_RATIO = 0.40;
    private static final double WALKING_SPEED_MPS = 1.25;
    private static final double MIN_SAFE_SEPARATION_M = 30;
    private static final double STRICT_AVOIDANCE_MIN_RADIUS_M = 45;
    private static final double STRICT_AVOIDANCE_MAX_RADIUS_M = 80;
    private static final double CORE_AVOIDANCE_MIN_RADIUS_M = 25;
    private static final double CORE_AVOIDANCE_MAX_RADIUS_M = 40;
    private static final int RESULT_VALID_SECONDS = 10;
    private static final int RETRY_AFTER_SECONDS = 25;

    private final WalkSessionAccessPort walkSessionPort;
    private final PresenceLocationStore presenceLocationStore;
    private final SafeDetourSnapPort snapPort;
    private final SafeDetourRouteRepository routeRepository;

    public SafeDetourService(
            WalkSessionAccessPort walkSessionPort,
            PresenceLocationStore presenceLocationStore,
            SafeDetourSnapPort snapPort,
            SafeDetourRouteRepository routeRepository
    ) {
        this.walkSessionPort = walkSessionPort;
        this.presenceLocationStore = presenceLocationStore;
        this.snapPort = snapPort;
        this.routeRepository = routeRepository;
    }

    public SafeDetourResponse find(long userId, long sessionId, SafeDetourRequest request) {
        validateSession(userId, sessionId);
        OffsetDateTime now = OffsetDateTime.now();
        if ("LEAVING".equals(request.alertTrend())) {
            return response(request.requestId(), "KEEP_ROUTE",
                    "다른 강아지가 멀어지고 있어요. 현재 경로를 유지해도 괜찮아요.",
                    null, null, null, List.of(), now, RETRY_AFTER_SECONDS);
        }

        Optional<PresenceLocation> originResult = presenceLocationStore.findLocation(sessionId);
        if (originResult.isEmpty() || originResult.get().updatedAt().isBefore(now.minusSeconds(15))) {
            return response(request.requestId(), "KEEP_ROUTE",
                    "최신 위치를 확인하지 못했어요. 현재 경로를 유지해 주세요.",
                    null, null, null, List.of(), now, 5);
        }
        PresenceLocation origin = originResult.get();
        Optional<NearbyPresenceLocation> candidateResult = presenceLocationStore
                .findNearby(origin, 550, 6).stream()
                .filter(candidate -> candidate.userId() != userId)
                .min(Comparator.comparingDouble(candidate -> haversineMeters(
                        origin.latitude(), origin.longitude(), candidate.latitude(), candidate.longitude())));
        if (candidateResult.isEmpty()) {
            return response(request.requestId(), "KEEP_ROUTE",
                    "주변 강아지가 멀어졌어요. 현재 경로를 유지해도 괜찮아요.",
                    null, null, null, List.of(), now, RETRY_AFTER_SECONDS);
        }

        RejoinTarget rejoin = selectRejoinTarget(request.remainingRoute());
        if (rejoin == null) {
            return response(request.requestId(), "KEEP_ROUTE",
                    "남은 경로가 짧아 현재 경로를 유지하는 편이 좋아요.",
                    null, null, null, List.of(), now, RETRY_AFTER_SECONDS);
        }

        Optional<SafeDetourSnapPort.SnappedPoint> startResult = snapPort.snap(
                origin.latitude(), origin.longitude(), Math.max(60, origin.accuracyMeters() + 25));
        Optional<SafeDetourSnapPort.SnappedPoint> endResult = snapPort.snap(
                rejoin.point().lat().doubleValue(), rejoin.point().lon().doubleValue(), 60);
        if (startResult.isEmpty() || endResult.isEmpty()) {
            return noRoute(request.requestId(), now);
        }

        SafeDetourSnapPort.SnappedPoint start = startResult.get();
        SafeDetourSnapPort.SnappedPoint end = endResult.get();
        Optional<SafeDetourPath> directResult = routeRepository.findPath(
                start.nodeId(), end.nodeId(), Set.of());
        if (directResult.isEmpty()) return noRoute(request.requestId(), now);

        NearbyPresenceLocation candidate = candidateResult.get();
        double separationM = haversineMeters(origin.latitude(), origin.longitude(),
                candidate.latitude(), candidate.longitude());
        if (separationM < MIN_SAFE_SEPARATION_M) {
            return waitResponse(request.requestId(),
                    "다른 강아지가 가까이 있어요. 안전한 거리를 확보한 뒤 다시 안내할게요.", now);
        }

        Optional<PresenceLocation> candidateLocation = presenceLocationStore.findLocation(candidate.sessionId());
        double strictRadiusM = Math.min(STRICT_AVOIDANCE_MAX_RADIUS_M,
                Math.max(STRICT_AVOIDANCE_MIN_RADIUS_M, candidate.accuracyMeters() + 35));
        Set<Long> excluded = avoidanceSegments(candidate, candidateLocation, strictRadiusM);

        Optional<SafeDetourPath> detourResult = routeRepository.findPath(
                start.nodeId(), end.nodeId(), excluded);
        if (detourResult.isEmpty()) {
            double coreRadiusM = Math.min(CORE_AVOIDANCE_MAX_RADIUS_M,
                    Math.max(CORE_AVOIDANCE_MIN_RADIUS_M, candidate.accuracyMeters() + 18));
            Set<Long> coreExcluded = avoidanceSegments(candidate, candidateLocation, coreRadiusM);
            if (!coreExcluded.equals(excluded)) {
                detourResult = routeRepository.findPath(start.nodeId(), end.nodeId(), coreExcluded);
            }
        }
        if (detourResult.isEmpty()) {
            return waitResponse(request.requestId(),
                    "주변에 안전하게 우회할 수 있는 길이 없어요. 잠시 거리를 두고 기다려 주세요.", now);
        }
        SafeDetourPath direct = directResult.get();
        SafeDetourPath detour = detourResult.get();
        double addedDistanceM = Math.max(0, detour.lengthM() - direct.lengthM());
        double allowedAddedM = Math.min(MAX_ADDED_DISTANCE_M, direct.lengthM() * MAX_DETOUR_RATIO);
        if (addedDistanceM > allowedAddedM) {
            return waitResponse(request.requestId(),
                    "지금 우회하면 길이 많이 멀어져요. 잠시 기다린 뒤 다시 확인해 주세요.", now);
        }

        List<SafeDetourPointResponse> route = combineRoute(origin, detour.coordinates(), rejoin.suffix());
        String maneuver = firstManeuver(origin, route);
        int addedDistance = (int) Math.round(addedDistanceM);
        int addedDuration = (int) Math.round(addedDistanceM / WALKING_SPEED_MPS);
        String message = maneuverMessage(maneuver, addedDuration);
        return response(request.requestId(), "DETOUR", message, maneuver,
                addedDistance, addedDuration, route, now, RETRY_AFTER_SECONDS);
    }

    private Set<Long> avoidanceSegments(
            NearbyPresenceLocation candidate,
            Optional<PresenceLocation> candidateLocation,
            double radiusM
    ) {
        Set<Long> excluded = new LinkedHashSet<>(routeRepository.findWalkableSegmentsNear(
                candidate.latitude(), candidate.longitude(), radiusM));
        candidateLocation
                .filter(location -> location.headingDegrees() != null && !location.stationary())
                .ifPresent(location -> {
                    SafeDetourPointResponse forward = project(
                            location.latitude(), location.longitude(), location.headingDegrees(), 35);
                    excluded.addAll(routeRepository.findWalkableSegmentsNear(
                            forward.lat(), forward.lon(), radiusM * 0.8));
                });
        return excluded;
    }

    private void validateSession(long userId, long sessionId) {
        WalkSessionSnapshot session = walkSessionPort.find(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        if (session.userId() != userId) {
            throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
        }
        if (!session.active() || session.paused()) {
            throw new BusinessException(PresenceErrorCode.PRESENCE_UPDATE_NOT_ALLOWED);
        }
        if (!"distance".equals(session.mode())) {
            throw new BusinessException(PresenceErrorCode.PRESENCE_MODE_DISABLED);
        }
    }

    private static RejoinTarget selectRejoinTarget(List<SafeDetourPointRequest> route) {
        if (route.size() < 2) return null;
        double traversedM = 0;
        for (int index = 1; index < route.size(); index++) {
            SafeDetourPointRequest from = route.get(index - 1);
            SafeDetourPointRequest to = route.get(index);
            double segmentM = haversineMeters(from.lat().doubleValue(), from.lon().doubleValue(),
                    to.lat().doubleValue(), to.lon().doubleValue());
            if (traversedM + segmentM >= REJOIN_DISTANCE_M) {
                double ratio = segmentM <= 0 ? 1 : (REJOIN_DISTANCE_M - traversedM) / segmentM;
                SafeDetourPointRequest point = new SafeDetourPointRequest(
                        java.math.BigDecimal.valueOf(from.lat().doubleValue()
                                + (to.lat().doubleValue() - from.lat().doubleValue()) * ratio),
                        java.math.BigDecimal.valueOf(from.lon().doubleValue()
                                + (to.lon().doubleValue() - from.lon().doubleValue()) * ratio));
                List<SafeDetourPointRequest> suffix = new ArrayList<>();
                suffix.add(point);
                suffix.addAll(route.subList(index, route.size()));
                return new RejoinTarget(point, suffix);
            }
            traversedM += segmentM;
        }
        return traversedM >= MIN_REJOIN_DISTANCE_M
                ? new RejoinTarget(route.getLast(), List.of(route.getLast()))
                : null;
    }

    private static List<SafeDetourPointResponse> combineRoute(
            PresenceLocation origin,
            List<SafeDetourPointResponse> detour,
            List<SafeDetourPointRequest> suffix
    ) {
        List<SafeDetourPointResponse> combined = new ArrayList<>();
        append(combined, new SafeDetourPointResponse(origin.latitude(), origin.longitude()));
        detour.forEach(point -> append(combined, point));
        suffix.forEach(point -> append(combined, new SafeDetourPointResponse(
                point.lat().doubleValue(), point.lon().doubleValue())));
        return List.copyOf(combined);
    }

    private static void append(List<SafeDetourPointResponse> points, SafeDetourPointResponse point) {
        if (points.isEmpty() || haversineMeters(points.getLast().lat(), points.getLast().lon(),
                point.lat(), point.lon()) >= 0.5) {
            points.add(point);
        }
    }

    private static String firstManeuver(PresenceLocation origin, List<SafeDetourPointResponse> route) {
        if (origin.headingDegrees() == null || route.size() < 2) return "STRAIGHT";
        SafeDetourPointResponse target = route.stream()
                .skip(1)
                .filter(point -> haversineMeters(origin.latitude(), origin.longitude(),
                        point.lat(), point.lon()) >= 8)
                .findFirst()
                .orElse(route.getLast());
        double bearing = bearingDegrees(origin.latitude(), origin.longitude(), target.lat(), target.lon());
        double delta = normalizeSignedDegrees(bearing - origin.headingDegrees());
        if (delta >= 35) return "RIGHT";
        if (delta <= -35) return "LEFT";
        return "STRAIGHT";
    }

    private static String maneuverMessage(String maneuver, int addedDurationSec) {
        String direction = switch (maneuver) {
            case "LEFT" -> "왼쪽 앞 길";
            case "RIGHT" -> "오른쪽 앞 길";
            default -> "앞쪽 길";
        };
        int minutes = Math.max(1, (int) Math.ceil(addedDurationSec / 60.0));
        return direction + "로 우회하면 약 " + minutes + "분 더 걸려요.";
    }

    private static SafeDetourResponse noRoute(String requestId, OffsetDateTime now) {
        return response(requestId, "NO_ROUTE",
                "새 경로를 찾지 못했어요. 기존 경로를 유지하며 잠시 거리를 두어 주세요.",
                null, null, null, List.of(), now, RETRY_AFTER_SECONDS);
    }

    private static SafeDetourResponse waitResponse(String requestId, String message, OffsetDateTime now) {
        return response(requestId, "WAIT", message, null, null, null, List.of(), now, RETRY_AFTER_SECONDS);
    }

    private static SafeDetourResponse response(
            String requestId,
            String decision,
            String message,
            String maneuver,
            Integer addedDistanceM,
            Integer addedDurationSec,
            List<SafeDetourPointResponse> route,
            OffsetDateTime now,
            int retryAfterSeconds
    ) {
        return new SafeDetourResponse(requestId, decision, message, maneuver,
                addedDistanceM, addedDurationSec, route,
                now.plusSeconds(RESULT_VALID_SECONDS), retryAfterSeconds);
    }

    private static SafeDetourPointResponse project(double lat, double lon, double heading, double distanceM) {
        double angular = distanceM / 6_371_000;
        double bearing = Math.toRadians(heading);
        double latitude = Math.toRadians(lat);
        double longitude = Math.toRadians(lon);
        double projectedLat = Math.asin(Math.sin(latitude) * Math.cos(angular)
                + Math.cos(latitude) * Math.sin(angular) * Math.cos(bearing));
        double projectedLon = longitude + Math.atan2(
                Math.sin(bearing) * Math.sin(angular) * Math.cos(latitude),
                Math.cos(angular) - Math.sin(latitude) * Math.sin(projectedLat));
        return new SafeDetourPointResponse(Math.toDegrees(projectedLat), Math.toDegrees(projectedLon));
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double latDelta = Math.toRadians(lat2 - lat1);
        double lonDelta = Math.toRadians(lon2 - lon1);
        double fromLat = Math.toRadians(lat1);
        double toLat = Math.toRadians(lat2);
        double haversine = Math.pow(Math.sin(latDelta / 2), 2)
                + Math.cos(fromLat) * Math.cos(toLat) * Math.pow(Math.sin(lonDelta / 2), 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    private static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double fromLat = Math.toRadians(lat1);
        double toLat = Math.toRadians(lat2);
        double lonDelta = Math.toRadians(lon2 - lon1);
        double y = Math.sin(lonDelta) * Math.cos(toLat);
        double x = Math.cos(fromLat) * Math.sin(toLat)
                - Math.sin(fromLat) * Math.cos(toLat) * Math.cos(lonDelta);
        return (Math.toDegrees(Math.atan2(y, x)) % 360 + 360) % 360;
    }

    private static double normalizeSignedDegrees(double degrees) {
        return (degrees + 540) % 360 - 180;
    }

    private record RejoinTarget(
            SafeDetourPointRequest point,
            List<SafeDetourPointRequest> suffix
    ) {
    }
}
