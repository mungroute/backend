package com.mungroute.place.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SpringDataCuratedPlaceMenuRepository extends JpaRepository<CuratedPlaceMenuEntity, String> {
    @EntityGraph(attributePaths = "items")
    @Query("SELECT menu FROM CuratedPlaceMenuEntity menu WHERE menu.contentId = :contentId")
    Optional<CuratedPlaceMenuEntity> findWithItemsByContentId(@Param("contentId") String contentId);
}
