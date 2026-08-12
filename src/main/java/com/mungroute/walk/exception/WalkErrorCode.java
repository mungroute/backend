package com.mungroute.walk.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum WalkErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    WALK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 산책에 접근할 권한이 없습니다."),
    ACTIVE_WALK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 산책이 있습니다."),
    WALK_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND,"산책 세션을 찾을 수 없습니다."),
    WALK_SESSION_ALREADY_ENDED(HttpStatus.CONFLICT,"이미 종료된 산책입니다."),
    INVALID_RECORDED_AT(HttpStatus.valueOf(422),"GPS 기록 시각이 허용 범위를 벗어났습니다.");

    private final HttpStatus status;
    private final String message;

    WalkErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }



}
