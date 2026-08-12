package com.mungroute.auth.repository;

import com.mungroute.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findTopByUser_UserIdOrderByPasswordResetTokenIdDesc(Long userId);

    Optional<PasswordResetToken> findByResetTokenHash(String resetTokenHash);
}
