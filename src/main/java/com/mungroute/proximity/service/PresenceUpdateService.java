package com.mungroute.proximity.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.dto.response.NearbyPresenceResponse;
import com.mungroute.proximity.dto.response.PresenceUpdateResponse;
import com.mungroute.proximity.exception.PresenceErrorCode;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.NearbyPresenceLocation;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.proximity.store.PresenceSessionState;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.mungroute.proximity.service.PresenceMetrics.Stage.CLASSIFICATION;
import static com.mungroute.proximity.service.PresenceMetrics.Stage.DB_END_EVENTS;
import static com.mungroute.proximity.service.PresenceMetrics.Stage.DB_NOTIFICATION;
import static com.mungroute.proximity.service.PresenceMetrics.Stage.REDIS_DISTANCE_HISTORY;
import static com.mungroute.proximity.service.PresenceMetrics.Stage.REDIS_GEO_SEARCH;
import static com.mungroute.proximity.service.PresenceMetrics.Stage.REDIS_LOCATION_UPDATE;
import static com.mungroute.proximity.service.PresenceMetrics.Stage.REDIS_SESSION_SYNC;
import static com.mungroute.proximity.service.PresenceMetrics.Stage.SESSION_VALIDATION;

@Service
public class PresenceUpdateService {

    private static final int REDIS_CANDIDATE_LIMIT = 101;
    private static final int RISK_EVALUATION_LIMIT = 12;
    private static final int RESPONSE_LIMIT = 3;
    private static final int CANDIDATE_ACCURACY_BUFFER_METERS = 50;
    private static final Set<String> SAFETY_VISIBLE_MODES = Set.of(
            WalkMode.OFF.getValue(),
            WalkMode.DISTANCE.getValue(),
            WalkMode.MEET.getValue()
    );
    private final WalkSessionRepository walkSessionRepository;
    private final PresenceRepository presenceRepository;
    private final PresenceLocationStore presenceLocationStore;
    private final PresenceMetrics metrics;

    public PresenceUpdateService(
            WalkSessionRepository walkSessionRepository,
            PresenceRepository presenceRepository,
            PresenceLocationStore presenceLocationStore,
            PresenceMetrics metrics
    ) {
        this.walkSessionRepository = walkSessionRepository;
        this.presenceRepository = presenceRepository;
        this.presenceLocationStore = presenceLocationStore;
        this.metrics = metrics;
    }

    public PresenceUpdateResponse update(long userId, PresenceUpdateRequest request) {
        validateTimestamp(request.measuredAt());
        OffsetDateTime updatedAt = OffsetDateTime.now();
        String mode = metrics.record(SESSION_VALIDATION,
                () -> validateColdOrCachedSession(userId, request, updatedAt));

        PresenceLocation origin = new PresenceLocation(
                request.sessionId(),
                userId,
                mode,
                request.lon().doubleValue(),
                request.lat().doubleValue(),
                request.accuracy().doubleValue(),
                request.heading() == null ? null : request.heading().doubleValue(),
                request.stationary(),
                updatedAt
        );
        metrics.record(REDIS_LOCATION_UPDATE, () -> presenceLocationStore.update(origin));

        List<NearbyPresenceLocation> nearbyCandidates = metrics.record(REDIS_GEO_SEARCH,
                () -> presenceLocationStore.findNearby(
                    origin,
                    request.radiusM() + CANDIDATE_ACCURACY_BUFFER_METERS,
                    REDIS_CANDIDATE_LIMIT
                ));
        List<ClassifiedPresence> classifiedCandidates = metrics.record(CLASSIFICATION, () -> nearbyCandidates.stream()
                .filter(candidate -> candidate.userId() != userId)
                .filter(candidate -> SAFETY_VISIBLE_MODES.contains(candidate.mode()))
                .map(candidate -> classify(origin, candidate, request.radiusM()))
                .filter(candidate -> candidate != null)
                .sorted(Comparator
                        .comparingInt(ClassifiedPresence::immediateRiskPriority)
                        .thenComparingInt(ClassifiedPresence::sortDistanceMeters))
                .toList());
        int nearbyCount = classifiedCandidates.size();
        List<ClassifiedPresence> classifiedNearby = classifiedCandidates.stream()
                .limit(RISK_EVALUATION_LIMIT)
                .map(candidate -> candidate.withTrend(metrics.record(REDIS_DISTANCE_HISTORY,
                        () -> presenceLocationStore.appendDistanceHistory(
                                origin.sessionId(), candidate.otherSessionId(), candidate.distanceMeters()
                        ))))
                .sorted(Comparator
                        .comparingInt(ClassifiedPresence::riskPriority)
                        .thenComparingInt(ClassifiedPresence::sortDistanceMeters))
                .limit(RESPONSE_LIMIT)
                .toList();

        List<ClassifiedPresence> finalClassifiedNearby = classifiedNearby;
        var transition = metrics.record(REDIS_SESSION_SYNC,
                () -> presenceLocationStore.synchronizeNearbySessions(
                        origin.sessionId(),
                        finalClassifiedNearby.stream().map(ClassifiedPresence::otherSessionId).toList()
                ));
        metrics.record(DB_END_EVENTS, () -> presenceRepository.endProximityEvents(
                origin.sessionId(), transition.leftSessionIds(), updatedAt));
        for (long enteredSessionId : transition.enteredSessionIds()) {
            classifiedNearby.stream()
                    .filter(candidate -> candidate.otherSessionId() == enteredSessionId)
                    .findFirst()
                    .ifPresent(candidate -> recordNotification(origin.sessionId(), candidate, updatedAt));
        }

        List<NearbyPresenceResponse> nearby = classifiedNearby.stream()
                .map(ClassifiedPresence::response)
                .toList();

        return new PresenceUpdateResponse(
                request.sessionId(),
                updatedAt,
                request.stationary() ? 10 : 4,
                nearby,
                nearbyCount,
                request.clientMessageId()
        );
    }

    private void recordNotification(
            long recipientSessionId,
            ClassifiedPresence candidate,
            OffsetDateTime notifiedAt
    ) {
        NearbyPresenceResponse response = candidate.response();
        metrics.record(DB_NOTIFICATION, () -> presenceRepository.recordProximityNotification(
                    recipientSessionId,
                    candidate.otherSessionId(),
                    response.distanceBand(),
                    response.directionOctant(),
                    response.directionSpread(),
                    response.trend(),
                    notifiedAt
                ));
    }

    private String validateColdOrCachedSession(
            long userId,
            PresenceUpdateRequest request,
            OffsetDateTime updatedAt
    ) {
        var cached = presenceLocationStore.findSession(request.sessionId());
        if (cached.isPresent()) {
            PresenceSessionState state = cached.get();
            if (state.userId() != userId) {
                throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
            }
            if (!WalkMode.DISTANCE.getValue().equals(state.mode())) {
                throw new BusinessException(PresenceErrorCode.PRESENCE_MODE_DISABLED);
            }
            return state.mode();
        }

        WalkSession session = walkSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        validateSession(session, userId);
        if (!presenceRepository.hasConsent(request.sessionId())) {
            throw new BusinessException(PresenceErrorCode.LOCATION_CONSENT_REQUIRED);
        }
        int updated = presenceRepository.updateTelemetry(
                request.sessionId(),
                request.accuracy(),
                request.heading(),
                request.stationary(),
                updatedAt
        );
        if (updated != 1) {
            throw new BusinessException(PresenceErrorCode.LOCATION_CONSENT_REQUIRED);
        }
        presenceLocationStore.cacheSession(new PresenceSessionState(
                request.sessionId(), userId, session.getMode().getValue()
        ));
        return session.getMode().getValue();
    }

    private ClassifiedPresence classify(
            PresenceLocation origin,
            NearbyPresenceLocation candidate,
            int radiusMeters
    ) {
        double distanceMeters = haversineMeters(
                origin.latitude(), origin.longitude(),
                candidate.latitude(), candidate.longitude()
        );
        if (distanceMeters > radiusMeters) {
            return null;
        }

        String distanceBand = distanceBand(distanceMeters);
        Integer octant = null;
        Integer spread = null;
        String reference = null;
        if (distanceMeters >= 30) {
            double bearing = bearingDegrees(
                    origin.latitude(), origin.longitude(),
                    candidate.latitude(), candidate.longitude()
            );
            double relative = origin.headingDegrees() == null
                    ? bearing
                    : normalizeDegrees(bearing - origin.headingDegrees());
            octant = (int) Math.floor((relative + 22.5) % 360 / 45.0);
            spread = Math.max(origin.accuracyMeters(), candidate.accuracyMeters()) <= 20 ? 45 : 90;
            reference = origin.headingDegrees() == null ? "MAP" : "HEADING";
        }

        NearbyPresenceResponse response = new NearbyPresenceResponse(
                distanceBand,
                octant,
                spread,
                reference,
                "NEW"
        );
        boolean likelyApproaching = isHeadingToward(
                origin.headingDegrees(), origin.stationary(),
                bearingDegrees(origin.latitude(), origin.longitude(), candidate.latitude(), candidate.longitude())
        ) || isHeadingToward(
                candidate.headingDegrees(), candidate.stationary(),
                bearingDegrees(candidate.latitude(), candidate.longitude(), origin.latitude(), origin.longitude())
        );
        return new ClassifiedPresence(
                (int) Math.round(distanceMeters),
                candidate.sessionId(),
                distanceMeters,
                likelyApproaching,
                response
        );
    }

    private void validateSession(WalkSession session, long userId) {
        if (!session.getUser().getUserId().equals(userId)) {
            throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
        }
        if (!session.isActive() || session.isPaused()) {
            throw new BusinessException(PresenceErrorCode.PRESENCE_UPDATE_NOT_ALLOWED);
        }
        if (session.getMode() != WalkMode.DISTANCE) {
            throw new BusinessException(PresenceErrorCode.PRESENCE_MODE_DISABLED);
        }
    }

    private void validateTimestamp(OffsetDateTime measuredAt) {
        OffsetDateTime now = OffsetDateTime.now();
        if (measuredAt.isBefore(now.minusSeconds(30)) || measuredAt.isAfter(now.plusSeconds(5))) {
            throw new BusinessException(PresenceErrorCode.INVALID_PRESENCE_TIMESTAMP);
        }
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

    static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double fromLat = Math.toRadians(lat1);
        double toLat = Math.toRadians(lat2);
        double lonDelta = Math.toRadians(lon2 - lon1);
        double y = Math.sin(lonDelta) * Math.cos(toLat);
        double x = Math.cos(fromLat) * Math.sin(toLat)
                - Math.sin(fromLat) * Math.cos(toLat) * Math.cos(lonDelta);
        return normalizeDegrees(Math.toDegrees(Math.atan2(y, x)));
    }

    private static double normalizeDegrees(double degrees) {
        return (degrees % 360 + 360) % 360;
    }

    private static boolean isHeadingToward(Double heading, boolean stationary, double targetBearing) {
        if (stationary || heading == null) return false;
        double delta = Math.abs((normalizeDegrees(targetBearing) - normalizeDegrees(heading) + 540) % 360 - 180);
        return delta <= 67.5;
    }

    private static String distanceBand(double distanceMeters) {
        if (distanceMeters < 30) return "VERY_CLOSE";
        if (distanceMeters < 50) return "BAND_30_50";
        if (distanceMeters < 100) return "BAND_50_100";
        return "BAND_100_500";
    }

    private static String trend(List<Double> history) {
        if (history.size() < 2) return "NEW";
        double change = history.get(history.size() - 1) - history.get(0);
        if (change <= -5) return "APPROACHING";
        if (change >= 5) return "LEAVING";
        return "STEADY";
    }

    private record ClassifiedPresence(
            int sortDistanceMeters,
            long otherSessionId,
            double distanceMeters,
            boolean likelyApproaching,
            NearbyPresenceResponse response
    ) {
        int immediateRiskPriority() {
            if ("VERY_CLOSE".equals(response.distanceBand())) return 0;
            if (likelyApproaching) return 1;
            return switch (response.distanceBand()) {
                case "BAND_30_50" -> 2;
                case "BAND_50_100" -> 3;
                default -> 4;
            };
        }

        int riskPriority() {
            if ("VERY_CLOSE".equals(response.distanceBand())) return 0;
            if ("APPROACHING".equals(response.trend())) return 1;
            if (likelyApproaching) return 2;
            return switch (response.trend()) {
                case "NEW" -> 3;
                case "STEADY" -> 4;
                case "LEAVING" -> 5;
                default -> 6;
            };
        }

        ClassifiedPresence withTrend(List<Double> history) {
            return new ClassifiedPresence(
                    sortDistanceMeters,
                    otherSessionId,
                    distanceMeters,
                    likelyApproaching,
                    new NearbyPresenceResponse(
                            response.distanceBand(),
                            response.directionOctant(),
                            response.directionSpread(),
                            response.directionReference(),
                            trend(history)
                    )
            );
        }
    }
}
