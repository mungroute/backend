package com.mungroute.user.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    DOG_NOT_FOUND(HttpStatus.NOT_FOUND, "반려견을 찾을 수 없습니다."),
    DOG_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 반려견을 변경할 권한이 없습니다."),
    DOG_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "산책에 함께할 반려견을 한 마리 이상 선택해 주세요."),
    DOG_SELECTION_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "선택한 반려견 정보를 확인해 주세요.");

    private final HttpStatus status;
    private final String message;

    UserErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
