package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.walk.domain.WalkMatchStatus;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class WalkFinalizationService {
    private final WalkSessionRepository walkSessionRepository;

    public WalkFinalizationService(WalkSessionRepository walkSessionRepository) {
        this.walkSessionRepository = walkSessionRepository;
    }

    @Transactional
    public FinalizationResult finalizeWalk(long userId, long sessionId, OffsetDateTime endedAt) {
        WalkSession session = walkSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        validateOwner(session, userId);
        WalkMatchStatus existingStatus = session.getMatchStatus();
        boolean finalizedNow = false;
        if (session.isActive()) {
            walkSessionRepository.finalizeWalkSession(sessionId, endedAt);
            finalizedNow = true;
        }
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
