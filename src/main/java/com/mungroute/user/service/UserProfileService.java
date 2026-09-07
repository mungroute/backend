package com.mungroute.user.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.dto.request.UpdateUserProfileRequest;
import com.mungroute.user.dto.response.UserResponse;
import com.mungroute.user.exception.UserErrorCode;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.user.port.UserSessionRevocationPort;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class UserProfileService {
    private final AppUserRepository userRepository;
    private final UserSessionRevocationPort sessionRevocationPort;
    private final PasswordEncoder passwordEncoder;

    public UserProfileService(AppUserRepository userRepository, UserSessionRevocationPort sessionRevocationPort,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionRevocationPort = sessionRevocationPort;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse update(long userId, UpdateUserProfileRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        String nickname = request.nickname() == null ? user.getNickname() : request.nickname().trim();
        if (!nickname.equals(user.getNickname())
                && userRepository.existsByNicknameAndUserIdNot(nickname, userId)) {
            throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
        }
        String image = request.profileImageUrl() == null ? user.getProfileImageUrl() : normalizeImage(request.profileImageUrl());
        user.updateProfile(nickname, image);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
        }
        return UserResponse.from(user);
    }

    public boolean isNicknameAvailable(long userId, String nickname) {
        return !userRepository.existsByNicknameAndUserIdNot(nickname.trim(), userId);
    }

    @Transactional
    public void deactivate(long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        sessionRevocationPort.revokeAllActive(userId, OffsetDateTime.now());
        user.deactivate(passwordEncoder.encode("deleted-" + userId + "-" + System.nanoTime()));
        userRepository.save(user);
    }

    private static String normalizeImage(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
