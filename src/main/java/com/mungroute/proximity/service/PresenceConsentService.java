package com.mungroute.proximity.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.proximity.dto.request.PresenceConsentRequest;
import com.mungroute.proximity.dto.response.PresenceConsentResponse;
import com.mungroute.proximity.exception.PresenceErrorCode;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.proximity.store.PresenceSessionState;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkSessionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class PresenceConsentService {
    private final WalkSessionRepository walkSessionRepository;
    private final PresenceRepository presenceRepository;
    private final PresenceLocationStore presenceLocationStore;

    public PresenceConsentService(
            WalkSessionRepository walkSessionRepository,
            PresenceRepository presenceRepository,
            PresenceLocationStore presenceLocationStore
    ) {
        this.walkSessionRepository = walkSessionRepository;
        this.presenceRepository = presenceRepository;
        this.presenceLocationStore = presenceLocationStore;
    }

    @Transactional
    public PresenceConsentResponse consent (
            long userId,
            PresenceConsentRequest request
    ) {
        WalkSession session = walkSessionRepository
                .findByIdForUpdate(request.sessionId())
                .orElseThrow(() ->
                        new BusinessException(
                                WalkErrorCode.WALK_SESSION_NOT_FOUND
                        ));

        validateOwner(session, userId);
        validateSessionState(session);
        validateMode(session);

        OffsetDateTime consentedAt = OffsetDateTime.now();

        int affectedRows = presenceRepository.upsertConsent(
                session.getSessionId(),
                userId,
                session.getMode().getValue(),
                consentedAt
        );

        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "위치정보 수집 동의 상태를 저장하지 못했습니다."
            );
        }

        presenceLocationStore.cacheSession(new PresenceSessionState(
                session.getSessionId(), userId, session.getMode().getValue()
        ));

        return new PresenceConsentResponse(
                session.getSessionId(),
                session.getMode().getValue(),
                consentedAt
        );

    }

    private void validateOwner(WalkSession session, long userId) {
        if (!session.getUser().getUserId().equals(userId)) {
            throw new BusinessException(
                    WalkErrorCode.WALK_ACCESS_DENIED
            );
        }
    }

    private void validateSessionState(WalkSession session) {
        if (!session.isActive()) {
            throw new BusinessException(
                    WalkErrorCode.WALK_SESSION_ALREADY_ENDED
            );
        }

        if (session.isPaused()) {
            throw new BusinessException(
                    WalkErrorCode.WALK_SESSION_PAUSED
            );
        }
    }

    private void validateMode(WalkSession session) {
        if (session.getMode() == WalkMode.OFF) {
            throw new BusinessException(
                    PresenceErrorCode.PRESENCE_MODE_DISABLED
            );
        }
    }
}
