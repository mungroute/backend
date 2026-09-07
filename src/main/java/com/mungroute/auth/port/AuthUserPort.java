package com.mungroute.auth.port;

import com.mungroute.user.domain.AppUser;

import java.util.Optional;

/** User capabilities required by authentication use cases. */
public interface AuthUserPort {
    Optional<AppUser> findById(long userId);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findActiveByEmail(String email);

    Optional<AppUser> findActiveById(long userId);

    AppUser saveAndFlush(AppUser user);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByPhoneNumber(String phoneNumber);
}
