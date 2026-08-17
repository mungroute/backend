package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocationStore;
import tools.jackson.databind.ObjectMapper;
import com.mungroute.course.matching.MapMatchingFailure;
import com.mungroute.course.matching.MapMatchingResult;
import com.mungroute.course.matching.SimpleMapMatchingService;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.dto.request.AddWalkPointRequest;
import com.mungroute.walk.dto.request.ChangeWalkModeRequest;
import com.mungroute.walk.dto.request.StartWalkRequest;
import com.mungroute.walk.dto.response.ChangeWalkModeResponse;
import com.mungroute.walk.dto.response.EndWalkResponse;
import com.mungroute.walk.dto.response.StartWalkResponse;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.EndWalkSummary;
import com.mungroute.walk.repository.WalkSessionRepository;
import com.mungroute.walk.repository.WalkTrackPointRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class WalkSessionService {
    private final AppUserRepository appUserRepository;
    private final WalkSessionRepository walkSessionRepository;
    private final WalkTrackPointRepository walkTrackPointRepository;
    private final WalkFinalizationService walkFinalizationService;
    private final SimpleMapMatchingService mapMatchingService;
    private final WalkMatchOutcomeService matchOutcomeService;
    private final ObjectMapper objectMapper;
    private final PresenceRepository presenceRepository;
    private final PresenceLocationStore presenceLocationStore;

    public WalkSessionService(
            AppUserRepository appUserRepository,
            WalkSessionRepository walkSessionRepository,
            WalkTrackPointRepository walkTrackPointRepository,
            WalkFinalizationService walkFinalizationService,
            SimpleMapMatchingService mapMatchingService,
            WalkMatchOutcomeService matchOutcomeService,
            ObjectMapper objectMapper,
            PresenceRepository presenceRepository,
            PresenceLocationStore presenceLocationStore
    ) {
        this.appUserRepository = appUserRepository;
        this.walkSessionRepository = walkSessionRepository;
        this.walkTrackPointRepository = walkTrackPointRepository;
        this.walkFinalizationService = walkFinalizationService;
        this.mapMatchingService = mapMatchingService;
        this.matchOutcomeService = matchOutcomeService;
        this.objectMapper = objectMapper;
        this.presenceRepository = presenceRepository;
        this.presenceLocationStore = presenceLocationStore;
    }

    // 사용자에게 새로운 활성 산책 세션 생성
    @Transactional
    public StartWalkResponse startWalk(Long userId, StartWalkRequest request) {
        AppUser user = appUserRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                WalkErrorCode.USER_NOT_FOUND
                        )
                );

        // Starting a walk is idempotent for a user. A browser refresh or a server
        // restart must recover the active session instead of trapping the user in
        // ACTIVE_WALK_ALREADY_EXISTS conflicts.
        var activeSession = walkSessionRepository.findActiveByUserIdForUpdate(userId);
        if (activeSession.isPresent()) {
            WalkSession session = activeSession.get();
            if (session.isPaused()) {
                walkSessionRepository.resumeWalkSession(session.getSessionId(), OffsetDateTime.now());
            }
            return StartWalkResponse.from(session);
        }

        WalkMode mode = WalkMode.from(request.mode());

        WalkSession walkSession = WalkSession.start(
                user,
                mode,
                OffsetDateTime.now()
        );


        WalkSession savedSession = walkSessionRepository.save(walkSession);

        return StartWalkResponse.from(savedSession);

    }

    // 활성 산책 세션에 GPS 포인트 추가
    @Transactional
    public void addPoint(
            Long userId,
            Long sessionId,
            AddWalkPointRequest request
    ) {
        OffsetDateTime receivedAt = OffsetDateTime.now();

        WalkSession walkSession = walkSessionRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(() ->
                        new BusinessException(
                                WalkErrorCode.WALK_SESSION_NOT_FOUND
                        ));

        validateOwner(walkSession, userId);

        if (!walkSession.isActive()) {
            throw new BusinessException(
                    WalkErrorCode.WALK_SESSION_ALREADY_ENDED
            );
        }

        if (walkSession.isPaused()) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_PAUSED);
        }

        validateRecordedAt(
                walkSession,
                request.recordedAt(),
                receivedAt
        );

        walkTrackPointRepository.insertPoint(
                walkSession.getSessionId(),
                request.recordedAt(),
                request.lon(),
                request.lat(),
                request.accuracy()
        );
    }

    // 산책 결과를 계산해 종료, 이미 종료된 세션은 기존 결과를 반환
    public EndWalkResponse endWalk(Long userId, Long sessionId) {
        WalkFinalizationService.FinalizationResult finalization = walkFinalizationService.finalizeWalk(
                userId,
                sessionId,
                OffsetDateTime.now()
        );
        if (finalization.matchingRequired()) {
            MapMatchingResult matchResult;
            try {
                matchResult = mapMatchingService.matchSession(sessionId);
            } catch (RuntimeException exception) {
                matchResult = new MapMatchingResult(
                        com.mungroute.walk.domain.WalkMatchStatus.FAILED,
                        java.util.List.of(),
                        false,
                        0,
                        0,
                        0,
                        MapMatchingFailure.INTERNAL_ERROR
                );
            }
            matchOutcomeService.save(sessionId, matchResult, OffsetDateTime.now());
        }

        EndWalkSummary summary = walkSessionRepository
                .findEndWalkSummary(sessionId)
                .orElseThrow(() ->
                        new BusinessException(
                                WalkErrorCode.WALK_SESSION_NOT_FOUND
                        )
                );

        presenceRepository.deleteBySessionId(sessionId);
        presenceLocationStore.delete(sessionId);

        return EndWalkResponse.from(summary, objectMapper);
    }

    @Transactional
    public com.mungroute.walk.dto.response.WalkStateResponse pauseWalk(Long userId, Long sessionId) {
        OffsetDateTime changedAt = OffsetDateTime.now();
        WalkSession session = walkSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        validateOwner(session, userId);
        if (!session.isActive()) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_ALREADY_ENDED);
        }
        walkSessionRepository.pauseWalkSession(sessionId, changedAt);
        presenceLocationStore.delete(sessionId);
        return new com.mungroute.walk.dto.response.WalkStateResponse(sessionId, "PAUSED", changedAt);
    }

    @Transactional
    public com.mungroute.walk.dto.response.WalkStateResponse resumeWalk(Long userId, Long sessionId) {
        OffsetDateTime changedAt = OffsetDateTime.now();
        WalkSession session = walkSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        validateOwner(session, userId);
        if (!session.isActive()) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_ALREADY_ENDED);
        }
        walkSessionRepository.resumeWalkSession(sessionId, changedAt);
        return new com.mungroute.walk.dto.response.WalkStateResponse(sessionId, "ACTIVE", changedAt);
    }

    @Transactional
    public ChangeWalkModeResponse changeMode(
            Long userId,
            Long sessionId,
            ChangeWalkModeRequest request
    ) {
        OffsetDateTime changedAt = OffsetDateTime.now();
        WalkSession session = walkSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));

        validateOwner(session, userId);
        if (!session.isActive()) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_ALREADY_ENDED);
        }

        WalkMode nextMode = WalkMode.from(request.mode());
        validateModeTransition(session, nextMode);
        session.changeMode(nextMode);

        if (nextMode == WalkMode.OFF) {
            presenceRepository.deleteBySessionId(sessionId);
            presenceLocationStore.delete(sessionId);
        } else {
            // 동의가 끝난 세션에 대해서만 active_presence 행이 존재한다.
            // 아직 동의하지 않은 경우 0행 갱신은 정상이며, 동의 API가 새 모드로 행을 생성한다.
            presenceRepository.updateMode(sessionId, nextMode.getValue(), changedAt);
        }

        return new ChangeWalkModeResponse(
                sessionId,
                nextMode.getValue(),
                session.getLockedMode() == null ? null : session.getLockedMode().getValue(),
                changedAt
        );
    }

    private void validateModeTransition(WalkSession session, WalkMode nextMode) {
        if (nextMode == WalkMode.OFF) {
            return;
        }

        if (session.getLockedMode() == null || session.getLockedMode() != nextMode) {
            throw new BusinessException(WalkErrorCode.WALK_MODE_CHANGE_NOT_ALLOWED);
        }
    }

    private void validateRecordedAt(
            WalkSession walkSession,
            OffsetDateTime recordedAt,
            OffsetDateTime receivedAt
    ) {
        boolean beforeWalkStarted = recordedAt.isBefore(walkSession.getStartedAt());
        boolean tooFarInFuture = recordedAt.isAfter(receivedAt.plusMinutes(5));

        if (beforeWalkStarted || tooFarInFuture) {
            throw new BusinessException(
                    WalkErrorCode.INVALID_RECORDED_AT
            );
        }
    }

    private void validateOwner(WalkSession walkSession, Long userId) {
        if (!walkSession.getUser().getUserId().equals(userId)) {
            throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
        }
    }
}
