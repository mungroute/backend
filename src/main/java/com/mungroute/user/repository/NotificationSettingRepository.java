package com.mungroute.user.repository;

import com.mungroute.user.domain.NotificationSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT setting
            FROM NotificationSetting setting
            WHERE setting.userId = :userId
            """)
    Optional<NotificationSetting> findByIdForUpdate(@Param("userId") long userId);
}
