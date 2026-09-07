package com.mungroute.user.adapter;

import com.mungroute.course.catalog.port.CourseOwnerLockPort;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.walk.port.WalkOwnerLockPort;
import org.springframework.stereotype.Component;

@Component
public class RepresentativeOwnerLockAdapter implements CourseOwnerLockPort, WalkOwnerLockPort {
    private final AppUserRepository userRepository;

    public RepresentativeOwnerLockAdapter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean lock(long userId) {
        return userRepository.findByIdForUpdate(userId).isPresent();
    }
}
