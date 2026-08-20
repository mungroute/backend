package com.mungroute.auth.controller;

import com.mungroute.auth.config.JwtProperties;
import com.mungroute.auth.dto.*;
import com.mungroute.auth.exception.AuthErrorCode;
import com.mungroute.auth.service.AuthService;
import com.mungroute.auth.service.IssuedAuth;
import com.mungroute.auth.service.PasswordResetService;
import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;

@Tag(name = "인증", description = "회원가입, 로그인, 토큰 및 데모 인증 API")
@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "MUNGROUTE_REFRESH";
    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, PasswordResetService passwordResetService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.jwtProperties = jwtProperties;
    }

    @Operation(summary = "회원가입", description = "가입과 동시에 로그인 토큰을 발급합니다.")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        IssuedAuth issued = authService.signup(request);
        return ResponseEntity.status(201).header(HttpHeaders.SET_COOKIE, refreshCookie(issued.refreshToken()).toString())
                .body(issued.response());
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        IssuedAuth issued = authService.login(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(issued.refreshToken()).toString())
                .body(issued.response());
    }

    @Operation(summary = "Access Token 재발급", description = "HttpOnly refresh cookie를 회전시켜 새 토큰을 발급합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        IssuedAuth issued = authService.refresh(requireRefreshToken(request));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(issued.refreshToken()).toString())
                .body(issued.response());
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(findRefreshToken(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
    }

    @Operation(summary = "현재 로그인 사용자 확인", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/session")
    public UserResponse session(@AuthenticationPrincipal MungrouteUserPrincipal principal) {
        return authService.getUser(principal.userId());
    }

    @GetMapping("/check-email")
    public AvailabilityResponse checkEmail(@RequestParam @Email String email) {
        return authService.checkEmail(email);
    }

    @GetMapping("/check-nickname")
    public AvailabilityResponse checkNickname(@RequestParam @Size(min = 2, max = 50) String nickname) {
        return authService.checkNickname(nickname);
    }

    @Operation(summary = "전화번호 데모 인증", description = "SMS 없이 전화번호 중복만 검사하며, 사용 가능하면 verified=true를 반환합니다.")
    @PostMapping("/phone-verifications")
    public PhoneVerificationResponse verifyPhone(@Valid @RequestBody PhoneVerificationRequest request) {
        return authService.verifyPhoneForDemo(request.phoneNumber());
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<PasswordResetRequestResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.accepted().body(passwordResetService.request(request));
    }

    @PostMapping("/password-reset/verify")
    public PasswordResetVerifyResponse verifyPasswordReset(@Valid @RequestBody PasswordResetVerifyRequest request) {
        return passwordResetService.verify(request);
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request);
        return ResponseEntity.noContent().build();
    }

    private String requireRefreshToken(HttpServletRequest request) {
        String token = findRefreshToken(request);
        if (token == null || token.isBlank()) throw new BusinessException(AuthErrorCode.TOKEN_INVALID);
        return token;
    }

    private String findRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> REFRESH_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(jwtProperties.cookieSecure())
                .sameSite(jwtProperties.cookieSameSite()).path("/api/auth")
                .maxAge(jwtProperties.refreshTokenTtl()).build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(jwtProperties.cookieSecure())
                .sameSite(jwtProperties.cookieSameSite()).path("/api/auth").maxAge(Duration.ZERO).build();
    }
}
