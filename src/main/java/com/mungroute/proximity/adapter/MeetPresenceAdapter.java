package com.mungroute.proximity.adapter;

import com.mungroute.meet.port.MeetPresencePort;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.proximity.store.PresenceSessionState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class MeetPresenceAdapter implements MeetPresencePort {
    private final PresenceRepository presenceRepository;
    private final PresenceLocationStore locationStore;

    public MeetPresenceAdapter(
            PresenceRepository presenceRepository,
            PresenceLocationStore locationStore
    ) {
        this.presenceRepository = presenceRepository;
        this.locationStore = locationStore;
    }

    @Override
    public void update(Location location) {
        locationStore.update(toStoreLocation(location));
    }

    @Override
    public Optional<SessionState> findSession(long sessionId) {
        return locationStore.findSession(sessionId)
                .map(session -> new SessionState(session.sessionId(), session.userId(), session.mode()));
    }

    @Override
    public void cacheSession(SessionState session) {
        locationStore.cacheSession(new PresenceSessionState(
                session.sessionId(), session.userId(), session.mode()));
    }

    @Override
    public List<NearbyLocation> findNearby(Location origin, int radiusMeters, int candidateLimit) {
        return locationStore.findNearby(toStoreLocation(origin), radiusMeters, candidateLimit).stream()
                .map(location -> new NearbyLocation(
                        location.sessionId(),
                        location.userId(),
                        location.mode(),
                        location.longitude(),
                        location.latitude(),
                        location.accuracyMeters(),
                        location.headingDegrees(),
                        location.stationary()
                ))
                .toList();
    }

    @Override
    public String issueCandidateRef(long viewerSessionId, long targetSessionId, long targetUserId) {
        return locationStore.issueMeetCandidateRef(viewerSessionId, targetSessionId, targetUserId);
    }

    @Override
    public Optional<CandidateTarget> resolveCandidateRef(long viewerSessionId, String candidateRef) {
        return locationStore.resolveMeetCandidateRef(viewerSessionId, candidateRef)
                .map(target -> new CandidateTarget(target.sessionId(), target.userId()));
    }

    @Override
    public Optional<Location> findLocation(long sessionId) {
        return locationStore.findLocation(sessionId).map(location -> new Location(
                location.sessionId(),
                location.userId(),
                location.mode(),
                location.longitude(),
                location.latitude(),
                location.accuracyMeters(),
                location.headingDegrees(),
                location.stationary(),
                location.updatedAt()
        ));
    }

    @Override
    public boolean hasConsent(long sessionId) {
        return presenceRepository.hasConsent(sessionId);
    }

    @Override
    public int updateTelemetry(
            long sessionId,
            BigDecimal accuracy,
            BigDecimal heading,
            boolean stationary,
            OffsetDateTime updatedAt
    ) {
        return presenceRepository.updateTelemetry(
                sessionId, accuracy, heading, stationary, updatedAt);
    }

    private PresenceLocation toStoreLocation(Location location) {
        return new PresenceLocation(
                location.sessionId(),
                location.userId(),
                location.mode(),
                location.longitude(),
                location.latitude(),
                location.accuracyMeters(),
                location.headingDegrees(),
                location.stationary(),
                location.updatedAt()
        );
    }
}
