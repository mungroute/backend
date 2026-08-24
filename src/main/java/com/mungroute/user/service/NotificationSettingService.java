package com.mungroute.user.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.domain.NotificationSetting;
import com.mungroute.user.dto.request.NotificationSettingRequest;
import com.mungroute.user.dto.response.NotificationSettingResponse;
import com.mungroute.user.exception.UserErrorCode;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.user.repository.NotificationSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationSettingService {
    private final NotificationSettingRepository repository;
    private final AppUserRepository userRepository;

    public NotificationSettingService(
            NotificationSettingRepository repository,
            AppUserRepository userRepository
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public NotificationSettingResponse get(long userId) {
        NotificationSetting setting = repository.findById(userId)
                .orElseGet(() -> createDefaultWithUserLock(userId));
        return NotificationSettingResponse.from(setting);
    }

    @Transactional
    public NotificationSettingResponse update(long userId, NotificationSettingRequest request) {
        NotificationSetting setting = repository.findByIdForUpdate(userId).orElse(null);
        boolean created = false;
        if (setting == null) {
            AppUser user = lockUser(userId);
            setting = repository.findByIdForUpdate(userId).orElse(null);
            if (setting == null) {
                setting = NotificationSetting.createDefault(user);
                created = true;
            }
        }
        setting.update(
                request.serviceEnabled(),
                request.distanceEnabled(),
                request.meetEnabled(),
                request.groupEnabled()
        );
        if (created) repository.save(setting);
        return NotificationSettingResponse.from(setting);
    }

    private NotificationSetting createDefaultWithUserLock(long userId) {
        AppUser user = lockUser(userId);
        return repository.findById(userId)
                .orElseGet(() -> repository.save(NotificationSetting.createDefault(user)));
    }

    private AppUser lockUser(long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
