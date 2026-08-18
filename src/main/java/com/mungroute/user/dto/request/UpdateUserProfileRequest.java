package com.mungroute.user.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하로 입력해 주세요.")
        String nickname,
        @Size(max = 2_000_000, message = "프로필 이미지가 너무 큽니다.")
        String profileImageUrl
) {
}
