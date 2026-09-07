package com.mungroute.auth.service;

import com.mungroute.auth.config.JwtProperties;
import com.mungroute.auth.domain.RefreshToken;
import com.mungroute.auth.dto.*;
import com.mungroute.auth.exception.AuthErrorCode;
import com.mungroute.auth.port.AuthUserPort;
import com.mungroute.auth.repository.RefreshTokenRepository;
import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.dto.response.UserResponse;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class AuthService {
    private final AuthUserPort userPort;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AccessTokenService accessTokenService;
    private final TokenHashService tokenHashService;
    private final JwtProperties jwtProperties;

    public AuthService(AuthUserPort userPort, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       AccessTokenService accessTokenService,
                       TokenHashService tokenHashService, JwtProperties jwtProperties) {
        this.userPort = userPort;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
        this.tokenHashService = tokenHashService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public IssuedAuth signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String nickname = request.nickname().trim();
        validateUnique(email, nickname, request.phoneNumber());
        AppUser user = AppUser.register(email, nickname, passwordEncoder.encode(request.password()), request.phoneNumber());
        try {
            userPort.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw mapDuplicateConstraint(exception);
        }
        return issueAuth(user);
    }

    @Transactional
    public IssuedAuth login(LoginRequest request) {
        try {
            MungrouteUserPrincipal principal = (MungrouteUserPrincipal) authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizeEmail(request.email()), request.password())
            ).getPrincipal();
            AppUser user = userPort.findById(principal.userId())
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
            return issueAuth(user);
        } catch (AuthenticationException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Transactional
    public IssuedAuth refresh(String rawRefreshToken) {
        OffsetDateTime now = OffsetDateTime.now();
        // Serialize rotation attempts for a token. Without the row lock, two
        // concurrent refresh requests can both observe the token as usable and
        // mint independent replacement sessions.
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(tokenHashService.hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TOKEN_INVALID));
        if (!current.isUsableAt(now)) {
            throw new BusinessException(AuthErrorCode.TOKEN_EXPIRED);
        }
        current.revoke(now);
        return issueAuth(current.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokenRepository.findByTokenHash(tokenHashService.hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(OffsetDateTime.now()));
    }

    @Transactional
    public UserResponse getUser(Long userId) {
        return userPort.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
    }

    public AvailabilityResponse checkEmail(String email) {
        return new AvailabilityResponse(!userPort.existsByEmail(normalizeEmail(email)));
    }

    public AvailabilityResponse checkNickname(String nickname) {
        return new AvailabilityResponse(!userPort.existsByNickname(nickname.trim()));
    }

    public PhoneVerificationResponse verifyPhoneForDemo(String phoneNumber) {
        boolean available = !userPort.existsByPhoneNumber(phoneNumber);
        return new PhoneVerificationResponse(available, available);
    }

    private IssuedAuth issueAuth(AppUser user) {
        String rawRefreshToken = tokenHashService.newOpaqueToken();
        String sessionHash = tokenHashService.hash(rawRefreshToken);
        refreshTokenRepository.save(RefreshToken.issue(user, sessionHash,
                OffsetDateTime.now().plus(jwtProperties.refreshTokenTtl())));
        return new IssuedAuth(AuthResponse.of(accessTokenService.issue(user, sessionHash), user), rawRefreshToken);
    }

    private void validateUnique(String email, String nickname, String phoneNumber) {
        if (userPort.existsByEmail(email)) throw new BusinessException(AuthErrorCode.EMAIL_DUPLICATED);
        if (userPort.existsByNickname(nickname)) throw new BusinessException(AuthErrorCode.NICKNAME_DUPLICATED);
        if (userPort.existsByPhoneNumber(phoneNumber)) throw new BusinessException(AuthErrorCode.PHONE_DUPLICATED);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private RuntimeException mapDuplicateConstraint(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("uk_app_user_email")) return new BusinessException(AuthErrorCode.EMAIL_DUPLICATED);
        if (message.contains("uk_app_user_nickname")) return new BusinessException(AuthErrorCode.NICKNAME_DUPLICATED);
        if (message.contains("uk_app_user_phone")) return new BusinessException(AuthErrorCode.PHONE_DUPLICATED);
        return exception;
    }
}
