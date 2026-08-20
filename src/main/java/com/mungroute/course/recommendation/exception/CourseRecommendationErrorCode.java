package com.mungroute.course.recommendation.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CourseRecommendationErrorCode implements ErrorCode {
    NO_WALKABLE_LINK(HttpStatus.UNPROCESSABLE_ENTITY, "현재 위치 주변에 연결 가능한 산책로가 없습니다."),
    NO_CANDIDATES(HttpStatus.UNPROCESSABLE_ENTITY, "선택한 시간에 맞는 추천 코스를 만들지 못했습니다."),
    RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "추천 요청을 찾을 수 없거나 만료되었습니다."),
    THERMAL_DATA_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "추천 코스의 온도 데이터를 계산할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    CourseRecommendationErrorCode(HttpStatus status, String message) {
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
