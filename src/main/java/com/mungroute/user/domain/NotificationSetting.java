package com.mungroute.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "notification_setting")
public class NotificationSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "service_enabled", nullable = false)
    private boolean serviceEnabled = true;

    @Column(name = "distance_enabled", nullable = false)
    private boolean distanceEnabled = true;

    @Column(name = "meet_enabled", nullable = false)
    private boolean meetEnabled = true;

    @Column(name = "group_enabled", nullable = false)
    private boolean groupEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NotificationSetting() {
    }

    private NotificationSetting(AppUser user) {
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
    }

    public static NotificationSetting createDefault(AppUser user) {
        return new NotificationSetting(user);
    }

    public void update(
            boolean serviceEnabled,
            boolean distanceEnabled,
            boolean meetEnabled,
            boolean groupEnabled
    ) {
        this.serviceEnabled = serviceEnabled;
        this.distanceEnabled = distanceEnabled;
        this.meetEnabled = meetEnabled;
        this.groupEnabled = groupEnabled;
        // 기존 JDBC 구현처럼 값이 같더라도 PATCH 요청 시각은 갱신한다.
        this.updatedAt = OffsetDateTime.now();
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isServiceEnabled() {
        return serviceEnabled;
    }

    public boolean isDistanceEnabled() {
        return distanceEnabled;
    }

    public boolean isMeetEnabled() {
        return meetEnabled;
    }

    public boolean isGroupEnabled() {
        return groupEnabled;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
