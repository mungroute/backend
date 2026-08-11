package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.dto.request.AddWalkPointRequest;
import com.mungroute.walk.dto.request.StartWalkRequest;
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

    public WalkSessionService(AppUserRepository appUserRepository, WalkSessionRepository walkSessionRepository, WalkTrackPointRepository walkTrackPointRepository) {
        this.appUserRepository = appUserRepository;
        this.walkSessionRepository = walkSessionRepository;
        this.walkTrackPointRepository = walkTrackPointRepository;
    }

    // 사용자에게 새로운 활성 산책 세션 생성
    @Transactional
    public StartWalkResponse startWalk(StartWalkRequest request) {
        AppUser user = appUserRepository
                .findByIdForUpdate(request.userId())
                .orElseThrow(() ->
                        new BusinessException(
                                WalkErrorCode.USER_NOT_FOUND
                        )
                );

        validateNoActiveWalk(request.userId());

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

        if (!walkSession.isActive()) {
            throw new BusinessException(
                    WalkErrorCode.WALK_SESSION_ALREADY_ENDED
            );
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
    @Transactional
    public EndWalkResponse endWalk(Long sessionId) {
        WalkSession walkSession = walkSessionRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(() ->
                        new BusinessException(
                                WalkErrorCode.WALK_SESSION_NOT_FOUND
                        )
                );

        if (walkSession.isActive()) {
            OffsetDateTime endedAt = OffsetDateTime.now();
            walkSessionRepository.finalizeWalkSession(
                    walkSession.getSessionId(),
                    endedAt
            );
        }

        EndWalkSummary summary = walkSessionRepository
                .findEndWalkSummary(sessionId)
                .orElseThrow(() ->
                        new BusinessException(
                                WalkErrorCode.WALK_SESSION_NOT_FOUND
                        )
                );

        return EndWalkResponse.from(summary);
    }

    // 사용자에게 이미 활성 산책이 있으면 시작을 거부함
    private void validateNoActiveWalk(Long userId) {
        boolean activeWalkExists = walkSessionRepository.existsByUser_UserIdAndEndedAtIsNull(userId);

        if (activeWalkExists) {
            throw new BusinessException(
                    WalkErrorCode.ACTIVE_WALK_ALREADY_EXISTS
            );
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
}
