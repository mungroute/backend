package com.mungroute.walk.dto.response;

import com.mungroute.walk.domain.WalkSession;

import java.time.OffsetDateTime;

// 산책 시작 성공 응답 DTO
public record StartWalkResponse(
        Long sessionId,
        OffsetDateTime startedAt,
        String mode,
        String lockedMode
){
    // 저장된 WalkSession을 API 응답으로 변환
    public static StartWalkResponse from(WalkSession session) {
        return new StartWalkResponse(
                session.getSessionId(),
                session.getStartedAt(),
                session.getMode().getValue(),
                session.getLockedMode() == null ? null : session.getLockedMode().getValue()
        );
    }
}
