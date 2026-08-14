package com.mungroute.walk.service;

import com.mungroute.course.matching.MapMatchingResult;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class WalkMatchOutcomeService {
    private final WalkSessionRepository walkSessionRepository;

    public WalkMatchOutcomeService(WalkSessionRepository walkSessionRepository) {
        this.walkSessionRepository = walkSessionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(long sessionId, MapMatchingResult result, OffsetDateTime matchedAt) {
        String failureReason = result.failure().name().equals("NONE") ? null : result.failure().name();
        walkSessionRepository.updateMatchOutcome(
                sessionId,
                result.status().name(),
                failureReason,
                matchedAt
        );
    }
}
