package com.mungroute.walk.repository;

import com.mungroute.walk.domain.WalkTrackPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface WalkTrackPointRepository extends JpaRepository<WalkTrackPoint, Long> {


    /**
     * EPSG:4326 좌표를 EPSG:5186으로 변환하여 저장한다.
     * @param sessionId
     * @param recordedAt
     * @param lon
     * @param lat
     * @param accuracy
     * @return 저장된 행 수
     */
    @Modifying
    @Query(
            value = """
                INSERT INTO walk_track_point (
                        session_id,
                        recorded_at,
                        location,
                        accuracy_m
                    )
                    VALUES (
                        :sessionId,
                        :recordedAt,
                        ST_Transform(
                            ST_SetSRID(
                                ST_MakePoint(:lon, :lat),
                                4326
                            ),
                            5186
                        ),
                        :accuracy
                    )
                    """,
            nativeQuery = true
    )
    int insertPoint(
            @Param("sessionId") Long sessionId,
            @Param("recordedAt") OffsetDateTime recordedAt,
            @Param("lon") Double lon,
            @Param("lat") Double lat,
            @Param("accuracy") BigDecimal accuracy
    );
}
