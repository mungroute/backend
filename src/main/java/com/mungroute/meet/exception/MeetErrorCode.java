package com.mungroute.meet.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MeetErrorCode implements ErrorCode {
    CANDIDATE_EXPIRED(HttpStatus.GONE, "주변 산책 친구 정보가 만료되었습니다. 다시 검색해 주세요."),
    PROFILE_REQUIRED(HttpStatus.CONFLICT, "만나보기에 공개할 반려견 프로필을 먼저 등록해 주세요."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "만나기 요청을 찾을 수 없습니다."),
    REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 만나기 요청을 처리할 권한이 없습니다."),
    REQUEST_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 진행 중인 만나기 요청이 있습니다."),
    REQUEST_STATE_INVALID(HttpStatus.CONFLICT, "현재 상태에서는 요청을 변경할 수 없습니다."),
    REQUEST_EXPIRED(HttpStatus.GONE, "만나기 요청이 만료되었습니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "차단 관계에서는 만나기를 요청할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    MeetErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
