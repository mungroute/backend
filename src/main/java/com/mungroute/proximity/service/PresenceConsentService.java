package com.mungroute.proximity.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.proximity.dto.request.PresenceConsentRequest;
import com.mungroute.proximity.dto.response.PresenceConsentResponse;
import com.mungroute.proximity.exception.PresenceErrorCode;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.proximity.store.PresenceSessionState;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.port.WalkSessionAccessPort;
import com.mungroute.walk.port.WalkSessionSnapshot;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class PresenceConsentService {
    private final WalkSessionAccessPort walkSessionPort;
    private final PresenceRepository presenceRepository;
    private final PresenceLocationStore presenceLocationStore;

    public PresenceConsentService(
            WalkSessionAccessPort walkSessionPort,
            PresenceRepository presenceRepository,
            PresenceLocationStore presenceLocationStore
    ) {
        this.walkSessionPort = walkSessionPort;
        this.presenceRepository = presenceRepository;
        this.presenceLocationStore = presenceLocationStore;
    }

    @Transactional
    public PresenceConsentResponse consent (
            long userId,
            PresenceConsentRequest request
    ) {
        WalkSessionSnapshot session = walkSessionPort
                .findForUpdate(request.sessionId())
                .orElseThrow(() ->
                        new BusinessException(
                                WalkErrorCode.WALK_SESSION_NOT_FOUND
                        ));

        validateOwner(session, userId);
        validateSessionState(session);
        validateMode(session);

        OffsetDateTime consentedAt = OffsetDateTime.now();

        int affectedRows = presenceRepository.upsertConsent(
                session.sessionId(),
                userId,
                session.mode(),
                consentedAt
        );

        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "위치정보 수집 동의 상태를 저장하지 못했습니다."
            );
        }

        presenceLocationStore.cacheSession(new PresenceSessionState(
                session.sessionId(), userId, session.mode()
        ));

        return new PresenceConsentResponse(
                session.sessionId(),
                session.mode(),
                consentedAt
        );

    }

    private void validateOwner(WalkSessionSnapshot session, long userId) {
        if (session.userId() != userId) {
            throw new BusinessException(
                    WalkErrorCode.WALK_ACCESS_DENIED
            );
        }
    }

    private void validateSessionState(WalkSessionSnapshot session) {
        if (!session.active()) {
            throw new BusinessException(
                    WalkErrorCode.WALK_SESSION_ALREADY_ENDED
            );
        }

        if (session.paused()) {
            throw new BusinessException(
                    WalkErrorCode.WALK_SESSION_PAUSED
            );
        }
    }

    private void validateMode(WalkSessionSnapshot session) {
        if ("off".equals(session.mode())) {
            throw new BusinessException(
                    PresenceErrorCode.PRESENCE_MODE_DISABLED
            );
        }
    }
}
