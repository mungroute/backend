package com.mungroute.user.service;

import com.mungroute.user.dto.request.NotificationSettingRequest;
import com.mungroute.user.dto.response.NotificationSettingResponse;
import com.mungroute.user.repository.NotificationSettingRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationSettingService {
    private final NotificationSettingRepository repository;

    public NotificationSettingService(NotificationSettingRepository repository) {
        this.repository = repository;
    }

    public NotificationSettingResponse get(long userId) {
        return repository.getOrCreate(userId);
    }

    public NotificationSettingResponse update(long userId, NotificationSettingRequest request) {
        return repository.update(userId, request);
    }
}
