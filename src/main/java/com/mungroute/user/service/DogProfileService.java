package com.mungroute.user.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.domain.DogProfile;
import com.mungroute.user.dto.request.DogProfileRequest;
import com.mungroute.user.dto.response.DogProfileResponse;
import com.mungroute.user.exception.UserErrorCode;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.user.repository.DogProfileRepository;
import com.mungroute.user.repository.JdbcWalkDogSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class DogProfileService {
    private final DogProfileRepository repository;
    private final AppUserRepository userRepository;
    private final JdbcWalkDogSnapshotRepository walkDogSnapshotRepository;

    public DogProfileService(
            DogProfileRepository repository,
            AppUserRepository userRepository,
            JdbcWalkDogSnapshotRepository walkDogSnapshotRepository
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.walkDogSnapshotRepository = walkDogSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public List<DogProfileResponse> list(long userId) {
        return repository.findAllActiveByUserId(userId).stream()
                .map(DogProfileService::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public DogProfileResponse get(long userId, long dogId) {
        return response(activeDog(userId, dogId));
    }

    @Transactional
    public DogProfileResponse create(long userId, DogProfileRequest request) {
        AppUser user = lockedUser(userId);
        List<DogProfile> currentDogs = repository.findAllActiveByUserId(userId);
        boolean makeDefault = request.isDefault() || currentDogs.isEmpty();
        if (makeDefault) clearDefaults(currentDogs);

        DogProfile dog = DogProfile.create(
                user,
                request.name(),
                request.breed(),
                request.birthDate(),
                request.profileImageUrl(),
                request.temperamentTags(),
                request.gender(),
                request.neutered(),
                request.introduction(),
                request.leashGreeting(),
                request.strangerResponse(),
                request.touchTolerance(),
                request.barkingLevel(),
                request.bitingLevel(),
                makeDefault
        );
        return response(repository.saveAndFlush(dog));
    }

    @Transactional
    public DogProfileResponse update(long userId, long dogId, DogProfileRequest request) {
        lockedUser(userId);
        DogProfile dog = activeDog(userId, dogId);
        boolean makeDefault = request.isDefault() || dog.isDefault();
        if (makeDefault) clearDefaults(repository.findAllActiveByUserId(userId));

        dog.updateProfile(
                request.name(),
                request.breed(),
                request.birthDate(),
                request.profileImageUrl(),
                request.temperamentTags(),
                request.gender(),
                request.neutered(),
                request.introduction(),
                request.leashGreeting(),
                request.strangerResponse(),
                request.touchTolerance(),
                request.barkingLevel(),
                request.bitingLevel()
        );
        dog.changeDefault(makeDefault);
        return response(repository.saveAndFlush(dog));
    }

    @Transactional
    public void delete(long userId, long dogId) {
        lockedUser(userId);
        List<DogProfile> currentDogs = repository.findAllActiveByUserId(userId);
        DogProfile dog = currentDogs.stream()
                .filter(candidate -> candidate.getDogId().equals(dogId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(UserErrorCode.DOG_NOT_FOUND));
        boolean promoteAnotherDog = dog.isDefault();
        dog.softDelete(OffsetDateTime.now());
        repository.flush();

        if (promoteAnotherDog) {
            currentDogs.stream()
                    .filter(candidate -> !candidate.getDogId().equals(dog.getDogId()))
                    .min(Comparator.comparing(DogProfile::getDogId))
                    .ifPresent(candidate -> {
                        candidate.changeDefault(true);
                        repository.saveAndFlush(candidate);
                    });
        }
    }

    @Transactional
    public void attachToWalk(long userId, long sessionId, List<Long> dogIds) {
        if (dogIds == null || dogIds.isEmpty()) return;
        lockedUser(userId);
        if (!walkDogSnapshotRepository.attachToWalk(userId, sessionId, dogIds)) {
            throw new BusinessException(UserErrorCode.DOG_SELECTION_INVALID);
        }
    }

    private AppUser lockedUser(long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private DogProfile activeDog(long userId, long dogId) {
        return repository.findActiveByUserIdAndDogId(userId, dogId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.DOG_NOT_FOUND));
    }

    private void clearDefaults(List<DogProfile> dogs) {
        boolean changed = false;
        for (DogProfile dog : dogs) {
            if (dog.isDefault()) {
                dog.changeDefault(false);
                changed = true;
            }
        }
        if (changed) repository.flush();
    }

    private static DogProfileResponse response(DogProfile dog) {
        return new DogProfileResponse(
                dog.getDogId(),
                dog.getName(),
                dog.getBreed(),
                dog.getBirthDate(),
                dog.getProfileImageUrl(),
                dog.getTemperamentTags(),
                dog.getGender(),
                dog.getNeutered(),
                dog.getIntroduction(),
                dog.getLeashGreeting(),
                dog.getStrangerResponse(),
                dog.getTouchTolerance(),
                dog.getBarkingLevel(),
                dog.getBitingLevel(),
                dog.isDefault(),
                dog.getCreatedAt(),
                dog.getUpdatedAt()
        );
    }
}
