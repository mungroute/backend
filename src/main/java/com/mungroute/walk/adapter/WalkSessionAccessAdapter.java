package com.mungroute.walk.adapter;

import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.port.WalkSessionAccessPort;
import com.mungroute.walk.port.WalkSessionSnapshot;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WalkSessionAccessAdapter implements WalkSessionAccessPort {
    private final WalkSessionRepository walkSessionRepository;

    public WalkSessionAccessAdapter(WalkSessionRepository walkSessionRepository) {
        this.walkSessionRepository = walkSessionRepository;
    }

    @Override
    public Optional<WalkSessionSnapshot> find(long sessionId) {
        return walkSessionRepository.findById(sessionId).map(this::snapshot);
    }

    @Override
    public Optional<WalkSessionSnapshot> findForUpdate(long sessionId) {
        return walkSessionRepository.findByIdForUpdate(sessionId).map(this::snapshot);
    }

    private WalkSessionSnapshot snapshot(WalkSession session) {
        return new WalkSessionSnapshot(
                session.getSessionId(),
                session.getUser().getUserId(),
                session.isActive(),
                session.isPaused(),
                session.getMode().getValue()
        );
    }
}
