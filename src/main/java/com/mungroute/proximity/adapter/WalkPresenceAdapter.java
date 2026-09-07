package com.mungroute.proximity.adapter;

import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.walk.port.WalkPresencePort;
import com.mungroute.walk.port.WalkPresenceUnavailableException;
import org.springframework.data.redis.RedisConnectionFailureException;
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
        try {
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
        } catch (RedisConnectionFailureException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void remove(long sessionId) {
        presenceRepository.deleteBySessionId(sessionId);
        deleteRealtimePresence(sessionId);
    }

    @Override
    public void pause(long sessionId, OffsetDateTime pausedAt) {
        presenceRepository.endAllProximityEvents(sessionId, pausedAt);
        deleteRealtimePresence(sessionId);
    }

    @Override
    public void updateMode(long sessionId, String mode, OffsetDateTime changedAt) {
        presenceRepository.updateMode(sessionId, mode, changedAt);
    }

    private void deleteRealtimePresence(long sessionId) {
        try {
            presenceLocationStore.delete(sessionId);
        } catch (RedisConnectionFailureException exception) {
            throw unavailable(exception);
        }
    }

    private WalkPresenceUnavailableException unavailable(RedisConnectionFailureException cause) {
        return new WalkPresenceUnavailableException("Realtime presence store is unavailable", cause);
    }
}
