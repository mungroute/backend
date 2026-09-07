package com.mungroute.proximity.adapter;

import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.walk.port.WalkPresencePort;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class WalkPresenceAdapter implements WalkPresencePort {
    private final PresenceRepository presenceRepository;
    private final PresenceLocationStore presenceLocationStore;

    public WalkPresenceAdapter(
            PresenceRepository presenceRepository,
            PresenceLocationStore presenceLocationStore
    ) {
        this.presenceRepository = presenceRepository;
        this.presenceLocationStore = presenceLocationStore;
    }

    @Override
    public void recordPassiveLocation(
            long sessionId,
            long userId,
            double longitude,
            double latitude,
            double accuracyMeters,
            OffsetDateTime recordedAt
    ) {
        presenceLocationStore.update(new PresenceLocation(
                sessionId,
                userId,
                "off",
                longitude,
                latitude,
                accuracyMeters,
                null,
                false,
                recordedAt
        ));
    }

    @Override
    public void remove(long sessionId) {
        presenceRepository.deleteBySessionId(sessionId);
        presenceLocationStore.delete(sessionId);
    }

    @Override
    public void pause(long sessionId, OffsetDateTime pausedAt) {
        presenceRepository.endAllProximityEvents(sessionId, pausedAt);
        presenceLocationStore.delete(sessionId);
    }

    @Override
    public void updateMode(long sessionId, String mode, OffsetDateTime changedAt) {
        presenceRepository.updateMode(sessionId, mode, changedAt);
    }
}
