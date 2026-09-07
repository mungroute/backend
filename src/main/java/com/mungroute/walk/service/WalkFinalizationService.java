package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.walk.domain.WalkMatchStatus;
import com.mungroute.walk.domain.WalkLifecycleEvent;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkCleanupOutboxRepository;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class WalkFinalizationService {
    private final WalkSessionRepository walkSessionRepository;
    private final WalkCleanupOutboxRepository cleanupOutboxRepository;
    private final WalkSessionStateMachine stateMachine;

    public WalkFinalizationService(
            WalkSessionRepository walkSessionRepository,
            WalkCleanupOutboxRepository cleanupOutboxRepository,
            WalkSessionStateMachine stateMachine
    ) {
        this.walkSessionRepository = walkSessionRepository;
        this.cleanupOutboxRepository = cleanupOutboxRepository;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public FinalizationResult finalizeWalk(long userId, long sessionId, OffsetDateTime endedAt) {
        WalkSession session = walkSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        validateOwner(session, userId);
        stateMachine.transition(session, WalkLifecycleEvent.END);
        WalkMatchStatus existingStatus = session.getMatchStatus();
        boolean finalizedNow = false;
        if (session.isActive()) {
            walkSessionRepository.finalizeWalkSession(sessionId, endedAt);
            finalizedNow = true;
        }
        // The task is inserted in the same transaction as finalization. If either
        // write rolls back, neither an ended walk nor an orphan cleanup task remains.
        // ON CONFLICT makes repeated end requests safe and preserves completion.
        cleanupOutboxRepository.enqueue(userId, sessionId, endedAt);
        return new FinalizationResult(finalizedNow, existingStatus == WalkMatchStatus.NOT_PERFORMED);
    }

    private void validateOwner(WalkSession session, long userId) {
        if (!session.getUser().getUserId().equals(userId)) {
            throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
        }
    }

    public record FinalizationResult(boolean finalizedNow, boolean matchingRequired) {
    }
}
