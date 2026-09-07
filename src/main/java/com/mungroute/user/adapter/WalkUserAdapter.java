package com.mungroute.user.adapter;

import com.mungroute.user.domain.AppUser;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.user.service.DogProfileService;
import com.mungroute.walk.port.WalkUserPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class WalkUserAdapter implements WalkUserPort {
    private final AppUserRepository appUserRepository;
    private final DogProfileService dogProfileService;

    public WalkUserAdapter(
            AppUserRepository appUserRepository,
            DogProfileService dogProfileService
    ) {
        this.appUserRepository = appUserRepository;
        this.dogProfileService = dogProfileService;
    }

    @Override
    public Optional<AppUser> findByIdForUpdate(long userId) {
        return appUserRepository.findByIdForUpdate(userId);
    }

    @Override
    public void attachDogs(long userId, long sessionId, List<Long> dogIds) {
        dogProfileService.attachToWalk(userId, sessionId, dogIds);
    }
}
