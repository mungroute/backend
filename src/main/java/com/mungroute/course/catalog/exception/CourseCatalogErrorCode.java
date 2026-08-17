package com.mungroute.course.catalog.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CourseCatalogErrorCode implements ErrorCode {
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다."),
    INVALID_COURSE_SOURCE(HttpStatus.BAD_REQUEST, "코스 출처가 올바르지 않습니다."),
    REPRESENTATIVE_COURSE_INELIGIBLE(HttpStatus.valueOf(422), "맵매칭된 순환 코스만 대표 코스로 지정할 수 있습니다."),
    COURSE_METRICS_UNAVAILABLE(HttpStatus.valueOf(422), "코스의 온도·그늘 지표를 계산할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    CourseCatalogErrorCode(HttpStatus status, String message) {
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
