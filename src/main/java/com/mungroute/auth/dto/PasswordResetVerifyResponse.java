package com.mungroute.auth.dto;

public record PasswordResetVerifyResponse(String resetToken, long expiresIn) {
}
