package com.mungroute.auth.service;

import com.mungroute.auth.config.PasswordResetProperties;
import com.mungroute.auth.domain.PasswordResetToken;
import com.mungroute.auth.dto.*;
import com.mungroute.auth.exception.AuthErrorCode;
import com.mungroute.auth.repository.PasswordResetTokenRepository;
import com.mungroute.auth.repository.RefreshTokenRepository;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.repository.AppUserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String ACCEPTED_MESSAGE = "가입된 계정이면 비밀번호 재설정 인증번호가 발급됩니다.";
    private final AppUserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHashService tokenHashService;
    private final PasswordResetProperties properties;

    public PasswordResetService(AppUserRepository userRepository, PasswordResetTokenRepository resetTokenRepository,
                                RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                                TokenHashService tokenHashService, PasswordResetProperties properties) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHashService = tokenHashService;
        this.properties = properties;
    }

    @Transactional
    public PasswordResetRequestResponse request(PasswordResetRequest request) {
        AppUser user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) return new PasswordResetRequestResponse(ACCEPTED_MESSAGE, null);
        String code = tokenHashService.newVerificationCode();
        resetTokenRepository.save(PasswordResetToken.issue(user, tokenHashService.hash(code),
                OffsetDateTime.now().plus(properties.verificationTtl())));
        log.info("Demo password reset verification code issued for userId={}: {}", user.getUserId(), code);
        return new PasswordResetRequestResponse(ACCEPTED_MESSAGE, properties.exposeCode() ? code : null);
    }

    @Transactional
    public PasswordResetVerifyResponse verify(PasswordResetVerifyRequest request) {
        AppUser user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PASSWORD_RESET_CODE_INVALID));
        PasswordResetToken token = resetTokenRepository
                .findTopByUser_UserIdOrderByPasswordResetTokenIdDesc(user.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PASSWORD_RESET_CODE_INVALID));
        OffsetDateTime now = OffsetDateTime.now();
        if (!token.canVerify(tokenHashService.hash(request.verificationCode()), now))
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_CODE_INVALID);
        String rawResetToken = tokenHashService.newOpaqueToken();
        token.verify(tokenHashService.hash(rawResetToken), now, now.plus(properties.resetTokenTtl()));
        return new PasswordResetVerifyResponse(rawResetToken, properties.resetTokenTtl().toSeconds());
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        PasswordResetToken token = resetTokenRepository.findByResetTokenHash(tokenHashService.hash(request.resetToken()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        OffsetDateTime now = OffsetDateTime.now();
        if (!token.canResetAt(now)) throw new BusinessException(AuthErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        token.getUser().changePassword(passwordEncoder.encode(request.newPassword()));
        token.markUsed(now);
        refreshTokenRepository.revokeAllActiveByUserId(token.getUser().getUserId(), now);
    }
}
