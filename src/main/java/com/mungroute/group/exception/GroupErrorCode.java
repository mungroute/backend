package com.mungroute.group.exception;

import com.mungroute.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum GroupErrorCode implements ErrorCode {
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."),
    GROUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 그룹에 접근할 권한이 없습니다."),
    GROUP_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "그룹 방장만 사용할 수 있는 기능입니다."),
    GROUP_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여 중인 그룹입니다."),
    GROUP_OPEN_JOIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "이 그룹은 초대 코드가 있어야 참여할 수 있습니다."),
    GROUP_INVITE_INVALID(HttpStatus.valueOf(422), "초대 코드가 만료되었거나 올바르지 않습니다."),
    GROUP_OWNER_LEAVE_NOT_ALLOWED(HttpStatus.CONFLICT, "방장은 그룹을 탈퇴할 수 없습니다. 그룹 삭제를 이용해 주세요."),
    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹 멤버를 찾을 수 없습니다."),
    GROUP_COURSE_ALREADY_SHARED(HttpStatus.CONFLICT, "이미 이 그룹에 공유된 코스입니다."),
    GROUP_SAVED_COURSE_RESHARE_NOT_ALLOWED(HttpStatus.CONFLICT, "그룹에서 저장한 코스는 다시 그룹에 공유할 수 없습니다."),
    GROUP_SHARED_COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "공유 코스를 찾을 수 없습니다."),
    GROUP_SHARED_COURSE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 공유 코스를 취소할 권한이 없습니다."),
    GROUP_COURSE_ALREADY_SAVED(HttpStatus.CONFLICT, "이미 내 코스로 저장한 공유 코스입니다."),
    GROUP_COURSE_SAVE_UNAVAILABLE(HttpStatus.valueOf(422), "이 코스는 내 코스로 복사할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    GroupErrorCode(HttpStatus status, String message) {
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
