package com.mungroute.user.dto.response;

import com.mungroute.user.domain.AppUser;

public record UserResponse(Long userId, String email, String nickname, String phoneNumber) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getUserId(), user.getEmail(), user.getNickname(), user.getPhoneNumber());
    }
}
