package com.mungroute.global.response;

/**
 * Bean Validation 실패 필드와 실패 이유를 표현한다.
 */
public record FieldErrorResponse(
        String field,
        String reason
) {
}
