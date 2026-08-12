package com.mungroute.auth.dto;

import com.mungroute.auth.service.AccessTokenService.IssuedAccessToken;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.dto.response.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    public static AuthResponse of(IssuedAccessToken token, AppUser user) {
        return new AuthResponse(token.value(), "Bearer", token.expiresIn(), UserResponse.from(user));
    }
}
