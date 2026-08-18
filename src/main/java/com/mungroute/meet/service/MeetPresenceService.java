package com.mungroute.meet.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.meet.dto.response.MeetCandidateResponse;
import com.mungroute.meet.dto.response.MeetConnectionResponse;
import com.mungroute.meet.dto.response.MeetPresenceResponse;
import com.mungroute.meet.dto.response.MeetProfileResponse;
import com.mungroute.meet.exception.MeetErrorCode;
import com.mungroute.meet.repository.MeetRepository;
import com.mungroute.meet.repository.MeetRequestRecord;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.exception.PresenceErrorCode;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkSessionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class MeetPresenceService {
    private static final int CANDIDATE_LIMIT = 10;
    private final WalkSessionRepository walkSessionRepository;
    private final PresenceRepository presenceRepository;
    private final PresenceLocationStore locationStore;
    private final MeetRepository meetRepository;

    public MeetPresenceService(WalkSessionRepository walkSessionRepository, PresenceRepository presenceRepository,
                               PresenceLocationStore locationStore, MeetRepository meetRepository) {
        this.walkSessionRepository = walkSessionRepository;
        this.presenceRepository = presenceRepository;
        this.locationStore = locationStore;
        this.meetRepository = meetRepository;
    }

    @Transactional
    public MeetPresenceResponse update(long userId, PresenceUpdateRequest request) {
        validateTimestamp(request.measuredAt());
        OffsetDateTime now = OffsetDateTime.now();
        validateSession(userId, request.sessionId(), request, now);
        PresenceLocation origin = new PresenceLocation(
                request.sessionId(), userId, WalkMode.MEET.getValue(),
                request.lon().doubleValue(), request.lat().doubleValue(), request.accuracy().doubleValue(),
                request.heading() == null ? null : request.heading().doubleValue(), request.stationary(), now
        );
        locationStore.update(origin);

        var accepted = meetRepository.findAcceptedForSession(request.sessionId());
        MeetConnectionResponse connection = accepted.flatMap(active -> connection(request.sessionId(), userId, active))
                .orElse(null);
        List<MeetCandidateResponse> candidates = connection == null
                ? locationStore.findNearby(origin, request.radiusM() + 50, CANDIDATE_LIMIT).stream()
                    .filter(candidate -> candidate.userId() != userId)
                    .filter(candidate -> WalkMode.MEET.getValue().equals(candidate.mode()))
                    .filter(candidate -> !meetRepository.isBlockedEither(userId, candidate.userId()))
                    .map(candidate -> new CandidateWithDistance(candidate,
                            haversine(origin.latitude(), origin.longitude(), candidate.latitude(), candidate.longitude())))
                    .filter(candidate -> candidate.distance <= request.radiusM())
                    .sorted(Comparator.comparingDouble(CandidateWithDistance::distance))
                    .limit(3)
                    .map(candidate -> new MeetCandidateResponse(
                            locationStore.issueMeetCandidateRef(origin.sessionId(), candidate.location.sessionId(), candidate.location.userId()),
                            distanceBand(candidate.distance), now.plusSeconds(30)
                    )).toList()
                : List.of();

        return new MeetPresenceResponse(request.sessionId(), now, request.stationary() ? 10 : 4, candidates, connection);
    }

    private java.util.Optional<MeetConnectionResponse> connection(long sessionId, long userId, MeetRequestRecord request) {
        long otherSessionId = request.otherSessionId(sessionId);
        long otherUserId = request.otherUserId(userId);
        if (meetRepository.isBlockedEither(userId, otherUserId)) return java.util.Optional.empty();
        return locationStore.findLocation(otherSessionId).flatMap(location ->
                meetRepository.findProfile(otherUserId).map(profile -> new MeetConnectionResponse(
                        request.requestId(), BigDecimal.valueOf(location.longitude()), BigDecimal.valueOf(location.latitude()),
                        location.updatedAt(), MeetProfileResponse.from(profile)
                )));
    }

    private void validateSession(long userId, long sessionId, PresenceUpdateRequest request, OffsetDateTime now) {
        var cached = locationStore.findSession(sessionId);
        if (cached.isPresent()) {
            if (cached.get().userId() != userId) throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
            if (!WalkMode.MEET.getValue().equals(cached.get().mode())) throw new BusinessException(PresenceErrorCode.PRESENCE_MODE_DISABLED);
            return;
        }
        WalkSession session = walkSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        if (!session.getUser().getUserId().equals(userId)) throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
        if (!session.isActive() || session.isPaused()) throw new BusinessException(PresenceErrorCode.PRESENCE_UPDATE_NOT_ALLOWED);
        if (session.getMode() != WalkMode.MEET) throw new BusinessException(PresenceErrorCode.PRESENCE_MODE_DISABLED);
        if (!presenceRepository.hasConsent(sessionId)) throw new BusinessException(PresenceErrorCode.LOCATION_CONSENT_REQUIRED);
        if (meetRepository.findProfile(userId).isEmpty()) throw new BusinessException(MeetErrorCode.PROFILE_REQUIRED);
        if (presenceRepository.updateTelemetry(sessionId, request.accuracy(), request.heading(), request.stationary(), now) != 1) {
            throw new BusinessException(PresenceErrorCode.LOCATION_CONSENT_REQUIRED);
        }
    }

    private void validateTimestamp(OffsetDateTime measuredAt) {
        OffsetDateTime now = OffsetDateTime.now();
        if (measuredAt.isBefore(now.minusSeconds(30)) || measuredAt.isAfter(now.plusSeconds(5))) {
            throw new BusinessException(PresenceErrorCode.INVALID_PRESENCE_TIMESTAMP);
        }
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double latDelta = Math.toRadians(lat2 - lat1);
        double lonDelta = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(latDelta / 2), 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(lonDelta / 2), 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String distanceBand(double meters) {
        if (meters < 30) return "VERY_CLOSE";
        if (meters < 50) return "BAND_30_50";
        if (meters < 100) return "BAND_50_100";
        return "BAND_100_500";
    }

    private record CandidateWithDistance(com.mungroute.proximity.store.NearbyPresenceLocation location, double distance) {}
}
