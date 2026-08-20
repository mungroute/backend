package com.mungroute.place.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PlaceErrorCode implements ErrorCode {
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 정보를 찾을 수 없습니다."),
    PLACE_PROVIDER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "장소 검색 서비스가 아직 설정되지 않았습니다."),
    PLACE_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 정보 제공기관과 통신할 수 없습니다."),
    PLACE_PROVIDER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "장소 정보 제공기관의 응답을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    PlaceErrorCode(HttpStatus status, String message) {
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
