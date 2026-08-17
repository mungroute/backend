package com.mungroute.course.draw.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CourseDrawErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    NO_WALKABLE_LINK(HttpStatus.UNPROCESSABLE_ENTITY, "주변에 연결 가능한 산책로가 없습니다."),
    NOT_CONNECTED(HttpStatus.UNPROCESSABLE_ENTITY, "두 지점을 걸어서 연결할 수 없습니다."),
    INVALID_COURSE_PATH(HttpStatus.UNPROCESSABLE_ENTITY, "코스의 경로 연결이 올바르지 않습니다."),
    THERMAL_DATA_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "코스의 지면온도 데이터를 계산할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    CourseDrawErrorCode(HttpStatus status, String message) {
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
