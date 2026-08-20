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

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @Column(name = "paused_duration_sec", nullable = false)
    private Integer pausedDurationSec = 0;

    @Column(name = "mode", nullable = false, length = 10)
    private WalkMode mode;

    @Column(name = "locked_mode", length = 10)
    private WalkMode lockedMode;

    @Column(name = "distance_m", precision = 8, scale = 1)
    private BigDecimal distanceM;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(
            name = "track_geom",
            columnDefinition = "geometry(LineString, 5186)"
    )
    private LineString trackGeom;

    @Column(name = "matched_segments")
    private Long[] matchedSegments;

    @Column(name = "is_loop")
    private Boolean loop;

    @Column(name = "is_saved", nullable = false)
    private boolean saved;

    @Column(name = "is_representative", nullable = false)
    private boolean representative;

    @Column(name = "course_name", length = 100)
    private String courseName;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 24)
    private WalkMatchStatus matchStatus = WalkMatchStatus.NOT_PERFORMED;

    @Column(name = "match_failure_reason", length = 50)
    private String matchFailureReason;

    @Column(name = "matched_at")
    private OffsetDateTime matchedAt;

    protected WalkSession() {
    }

    private WalkSession(
            AppUser user,
            WalkMode mode,
            OffsetDateTime startedAt
    ) {
        this.user = user;
        this.mode = mode;
        this.lockedMode = mode == WalkMode.OFF ? null : mode;
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

    public boolean isPaused() {
        return pausedAt != null;
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

    public WalkMode getLockedMode() {
        return lockedMode;
    }

    public void changeMode(WalkMode mode) {
        this.mode = mode;
    }

    public void enablePresenceMode(WalkMode mode) {
        if (mode == WalkMode.OFF) {
            throw new IllegalArgumentException("활성화할 주변 사용자 모드는 off일 수 없습니다.");
        }
        if (lockedMode != null && lockedMode != mode) {
            throw new IllegalStateException("이미 고정된 산책 모드와 다른 모드는 활성화할 수 없습니다.");
        }
        lockedMode = mode;
        this.mode = mode;
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

    public OffsetDateTime getPausedAt() {
        return pausedAt;
    }

    public Integer getPausedDurationSec() {
        return pausedDurationSec;
    }

    public Long[] getMatchedSegments() {
        return matchedSegments;
    }

    public Boolean getLoop() {
        return loop;
    }

    public boolean isSaved() {
        return saved;
    }

    public boolean isRepresentative() {
        return representative;
    }

    public String getCourseName() {
        return courseName;
    }

    public WalkMatchStatus getMatchStatus() {
        return matchStatus;
    }

    public String getMatchFailureReason() {
        return matchFailureReason;
    }

    public OffsetDateTime getMatchedAt() {
        return matchedAt;
    }
}
