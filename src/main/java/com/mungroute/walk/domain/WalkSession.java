package com.mungroute.walk.domain;

import com.mungroute.user.domain.AppUser;
import jakarta.persistence.*;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "walk_session")
public class WalkSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "mode", nullable = false, length = 10)
    private WalkMode mode;

    @Column(name = "distance_m", precision = 8, scale = 1)
    private BigDecimal distanceM;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(
            name = "track_geom",
            columnDefinition = "geometry(LineString, 5186)"
    )
    private LineString trackGeom;

    protected WalkSession() {
    }

    private WalkSession(
            AppUser user,
            WalkMode mode,
            OffsetDateTime startedAt
    ) {
        this.user = user;
        this.mode = mode;
        this.startedAt = startedAt;
    }

    // 새로운 활성 산책 세션 생성
    public static WalkSession start(
            AppUser user,
            WalkMode mode,
            OffsetDateTime startedAt
    ) {
        return new WalkSession(user, mode, startedAt);
    }

    // 아직 종료되지 않은 활성 산책인지 확인
    public boolean isActive() {
        return endedAt == null;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public AppUser getUser() {
        return user;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public WalkMode getMode() {
        return mode;
    }

    public BigDecimal getDistanceM() {
        return distanceM;
    }

    public Integer getDurationSec() {
        return durationSec;
    }

    public LineString getTrackGeom() {
        return trackGeom;
    }
}
