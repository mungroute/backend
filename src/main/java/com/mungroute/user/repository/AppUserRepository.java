package com.mungroute.user.repository;

import com.mungroute.user.domain.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByPhoneNumber(String phoneNumber);

    // 산책 시작을 직렬화하기 위해 사용자 행을 쓰기 잠금으로 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT user
        FROM AppUser user
        WHERE user.userId = :userId
        """)
    Optional<AppUser> findByIdForUpdate(
            @Param("userId") Long userId
    );


}
