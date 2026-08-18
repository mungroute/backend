package com.mungroute.user.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.dto.request.DogProfileRequest;
import com.mungroute.user.dto.response.DogProfileResponse;
import com.mungroute.user.exception.UserErrorCode;
import com.mungroute.user.repository.DogProfileRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DogProfileService {
    private final DogProfileRepository repository;

    public DogProfileService(DogProfileRepository repository) {
        this.repository = repository;
    }

    public List<DogProfileResponse> list(long userId) {
        return repository.findAll(userId);
    }

    public DogProfileResponse get(long userId, long dogId) {
        return repository.find(userId, dogId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.DOG_NOT_FOUND));
    }

    @Transactional
    public DogProfileResponse create(long userId, DogProfileRequest request) {
        boolean makeDefault = request.isDefault() || !repository.hasAny(userId);
        if (makeDefault) repository.clearDefault(userId);
        long dogId = repository.create(userId, request, makeDefault);
        return get(userId, dogId);
    }

    @Transactional
    public DogProfileResponse update(long userId, long dogId, DogProfileRequest request) {
        DogProfileResponse current = get(userId, dogId);
        boolean makeDefault = request.isDefault() || current.isDefault();
        if (makeDefault) repository.clearDefault(userId);
        if (repository.update(userId, dogId, request, makeDefault) != 1) {
            throw new BusinessException(UserErrorCode.DOG_NOT_FOUND);
        }
        return get(userId, dogId);
    }

    @Transactional
    public void delete(long userId, long dogId) {
        DogProfileResponse current = get(userId, dogId);
        if (repository.softDelete(userId, dogId) != 1) {
            throw new BusinessException(UserErrorCode.DOG_NOT_FOUND);
        }
        if (current.isDefault()) repository.promoteFirst(userId);
    }

    @Transactional
    public void attachToWalk(long userId, long sessionId, List<Long> dogIds) {
        if (dogIds == null || dogIds.isEmpty()) return;
        try {
            repository.attachToWalk(userId, sessionId, dogIds);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(UserErrorCode.DOG_SELECTION_INVALID);
        }
    }
}
