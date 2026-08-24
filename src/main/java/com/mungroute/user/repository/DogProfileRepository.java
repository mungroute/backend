package com.mungroute.user.repository;

import com.mungroute.user.domain.DogProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DogProfileRepository extends JpaRepository<DogProfile, Long> {

    @Query("""
            SELECT dog
            FROM DogProfile dog
            WHERE dog.user.userId = :userId
              AND dog.deletedAt IS NULL
            ORDER BY dog.defaultDog DESC, dog.dogId
            """)
    List<DogProfile> findAllActiveByUserId(@Param("userId") long userId);

    @Query("""
            SELECT dog
            FROM DogProfile dog
            WHERE dog.user.userId = :userId
              AND dog.dogId = :dogId
              AND dog.deletedAt IS NULL
            """)
    Optional<DogProfile> findActiveByUserIdAndDogId(
            @Param("userId") long userId,
            @Param("dogId") long dogId
    );
}
