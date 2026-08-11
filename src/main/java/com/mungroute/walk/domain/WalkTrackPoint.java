package com.mungroute.walk.domain;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "walk_track_point")
public class WalkTrackPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_id")
    private Long pointId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WalkSession walkSession;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(
            name = "location",
            nullable = false,
            columnDefinition = "geometry(Point, 5186)"
    )
    private Point location;

    // 예상 오차 범위 (단위 : metre)
    @Column(name = "accuracy_m", precision = 5, scale = 1)
    private BigDecimal accuracyM;

    protected WalkTrackPoint() {
    }

    public Long getPointId() {
        return pointId;
    }

    public WalkSession getWalkSession() {
        return walkSession;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public Point getLocation() {
        return location;
    }

    public BigDecimal getAccuracyM() {
        return accuracyM;
    }
}
