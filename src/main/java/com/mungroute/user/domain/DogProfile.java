package com.mungroute.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "dog_profile")
public class DogProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dog_id")
    private Long dogId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "breed", nullable = false, length = 80)
    private String breed;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "temperament_tags", nullable = false, columnDefinition = "varchar(30)[]")
    private String[] temperamentTags = new String[0];

    @Column(name = "gender", nullable = false, length = 10)
    private String gender = "UNKNOWN";

    @Column(name = "neutered")
    private Boolean neutered;

    @Column(name = "introduction", length = 50)
    private String introduction;

    @Column(name = "leash_greeting", nullable = false, length = 20)
    private String leashGreeting = "UNKNOWN";

    @Column(name = "stranger_response", nullable = false, length = 20)
    private String strangerResponse = "UNKNOWN";

    @Column(name = "touch_tolerance", nullable = false, length = 20)
    private String touchTolerance = "UNKNOWN";

    @Column(name = "barking_level", nullable = false, length = 20)
    private String barkingLevel = "UNKNOWN";

    @Column(name = "biting_level", nullable = false, length = 20)
    private String bitingLevel = "UNKNOWN";

    @Column(name = "is_default", nullable = false)
    private boolean defaultDog;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected DogProfile() {
    }

    private DogProfile(
            AppUser user,
            String name,
            String breed,
            LocalDate birthDate,
            String profileImageUrl,
            List<String> temperamentTags,
            String gender,
            Boolean neutered,
            String introduction,
            String leashGreeting,
            String strangerResponse,
            String touchTolerance,
            String barkingLevel,
            String bitingLevel,
            boolean defaultDog
    ) {
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
        changeProfile(
                name,
                breed,
                birthDate,
                profileImageUrl,
                temperamentTags,
                gender,
                neutered,
                introduction,
                leashGreeting,
                strangerResponse,
                touchTolerance,
                barkingLevel,
                bitingLevel
        );
        this.defaultDog = defaultDog;
    }

    public static DogProfile create(
            AppUser user,
            String name,
            String breed,
            LocalDate birthDate,
            String profileImageUrl,
            List<String> temperamentTags,
            String gender,
            Boolean neutered,
            String introduction,
            String leashGreeting,
            String strangerResponse,
            String touchTolerance,
            String barkingLevel,
            String bitingLevel,
            boolean defaultDog
    ) {
        return new DogProfile(
                user,
                name,
                breed,
                birthDate,
                profileImageUrl,
                temperamentTags,
                gender,
                neutered,
                introduction,
                leashGreeting,
                strangerResponse,
                touchTolerance,
                barkingLevel,
                bitingLevel,
                defaultDog
        );
    }

    public void updateProfile(
            String name,
            String breed,
            LocalDate birthDate,
            String profileImageUrl,
            List<String> temperamentTags,
            String gender,
            Boolean neutered,
            String introduction,
            String leashGreeting,
            String strangerResponse,
            String touchTolerance,
            String barkingLevel,
            String bitingLevel
    ) {
        ensureActive();
        changeProfile(
                name,
                breed,
                birthDate,
                profileImageUrl,
                temperamentTags,
                gender,
                neutered,
                introduction,
                leashGreeting,
                strangerResponse,
                touchTolerance,
                barkingLevel,
                bitingLevel
        );
    }

    public void changeDefault(boolean defaultDog) {
        ensureActive();
        this.defaultDog = defaultDog;
    }

    public void softDelete(OffsetDateTime deletedAt) {
        if (this.deletedAt != null) return;
        this.defaultDog = false;
        this.deletedAt = Objects.requireNonNull(deletedAt, "삭제 시각은 필수입니다.");
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    private void changeProfile(
            String name,
            String breed,
            LocalDate birthDate,
            String profileImageUrl,
            List<String> temperamentTags,
            String gender,
            Boolean neutered,
            String introduction,
            String leashGreeting,
            String strangerResponse,
            String touchTolerance,
            String barkingLevel,
            String bitingLevel
    ) {
        this.name = requireText(name, "이름");
        this.breed = requireText(breed, "견종");
        this.birthDate = Objects.requireNonNull(birthDate, "생년월일은 필수입니다.");
        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 오늘 이후일 수 없습니다.");
        }
        this.profileImageUrl = profileImageUrl == null || profileImageUrl.isBlank()
                ? null
                : profileImageUrl;
        List<String> normalizedTags = temperamentTags == null ? List.of() : temperamentTags.stream()
                .map(tag -> requireText(tag, "성향 태그"))
                .distinct()
                .toList();
        if (normalizedTags.size() > 5) {
            throw new IllegalArgumentException("성향 태그는 최대 5개까지 선택할 수 있습니다.");
        }
        this.temperamentTags = normalizedTags.toArray(String[]::new);
        this.gender = defaultValue(gender);
        this.neutered = neutered;
        this.introduction = normalizeOptionalText(introduction);
        this.leashGreeting = defaultValue(leashGreeting);
        this.strangerResponse = defaultValue(strangerResponse);
        this.touchTolerance = defaultValue(touchTolerance);
        this.barkingLevel = defaultValue(barkingLevel);
        this.bitingLevel = defaultValue(bitingLevel);
        // 기존 JDBC 구현처럼 값이 같은 수정 요청도 마지막 수정 시각을 남긴다.
        this.updatedAt = OffsetDateTime.now();
    }

    private void ensureActive() {
        if (deletedAt != null) {
            throw new IllegalStateException("삭제된 반려견 프로필은 변경할 수 없습니다.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
        return value.trim();
    }

    private static String defaultValue(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getDogId() {
        return dogId;
    }

    public AppUser getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getBreed() {
        return breed;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public List<String> getTemperamentTags() {
        return List.copyOf(Arrays.asList(temperamentTags));
    }

    public String getGender() { return gender; }
    public Boolean getNeutered() { return neutered; }
    public String getIntroduction() { return introduction; }
    public String getLeashGreeting() { return leashGreeting; }
    public String getStrangerResponse() { return strangerResponse; }
    public String getTouchTolerance() { return touchTolerance; }
    public String getBarkingLevel() { return barkingLevel; }
    public String getBitingLevel() { return bitingLevel; }

    public boolean isDefault() {
        return defaultDog;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
