package com.mungroute.user.adapter;

import com.mungroute.auth.port.AuthUserPort;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.repository.AppUserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthUserAdapter implements AuthUserPort {
    private final AppUserRepository userRepository;

    public AuthUserAdapter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<AppUser> findById(long userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<AppUser> findActiveByEmail(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email);
    }

    @Override
    public Optional<AppUser> findActiveById(long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public AppUser saveAndFlush(AppUser user) {
        return userRepository.saveAndFlush(user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }
}
