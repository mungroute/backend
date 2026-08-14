package com.mungroute.thermal.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ThermalErrorCode implements ErrorCode {
    ROUTE_SEGMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "경로 구간을 찾을 수 없습니다."),
    THERMAL_DATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "경로 구간의 온도 데이터가 준비되지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    ThermalErrorCode(HttpStatus status, String message) {
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
