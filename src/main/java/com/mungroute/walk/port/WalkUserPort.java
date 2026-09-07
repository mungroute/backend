package com.mungroute.walk.port;

import com.mungroute.user.domain.AppUser;

import java.util.List;
import java.util.Optional;

/** Consumer-owned boundary for the user data required by walk use cases. */
public interface WalkUserPort {
    Optional<AppUser> findByIdForUpdate(long userId);

    void attachDogs(long userId, long sessionId, List<Long> dogIds);
}
