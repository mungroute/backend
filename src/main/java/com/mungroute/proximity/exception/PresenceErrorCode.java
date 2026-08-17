package com.mungroute.proximity.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PresenceErrorCode implements ErrorCode {

    LOCATION_CONSENT_REQUIRED(
            HttpStatus.FORBIDDEN,
            "거리두기 위치정보 수집 동의가 필요합니다."
    ),

    PRESENCE_MODE_DISABLED(
            HttpStatus.CONFLICT,
            "거리두기 또는 만나보기 모드가 활성화되어 있지 않습니다."
    ),

    PRESENCE_UPDATE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "현재 산책 상태에서는 실시간 위치를 갱신할 수 없습니다."
    ),

    INVALID_PRESENCE_TIMESTAMP(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "실시간 위치 측정 시각이 허용 범위를 벗어났습니다."
    );

    private final HttpStatus status;
    private final String message;

    PresenceErrorCode(HttpStatus status, String message) {
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