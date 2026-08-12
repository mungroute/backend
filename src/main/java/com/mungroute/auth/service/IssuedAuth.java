package com.mungroute.auth.service;

import com.mungroute.auth.dto.AuthResponse;

public record IssuedAuth(AuthResponse response, String refreshToken) {
}
