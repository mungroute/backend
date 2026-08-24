package com.mungroute.user.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DogProfileTest {

    @Test
    void createsAndUpdatesProfileWithoutExposingMutableTags() {
        AppUser user = AppUser.register("owner@example.com", "보호자", "encoded", "01012345678");
        DogProfile dog = DogProfile.create(
                user, " 망고 ", " 골든 리트리버 ", LocalDate.of(2022, 5, 12),
                null, List.of("차분해요", "차분해요", "친구를 좋아해요"),
                "MALE", true, " 친구를 좋아해요 ", "LIKES", "NEUTRAL",
                "COMFORTABLE", "RARE", "NONE", true
        );

        assertThat(dog.getName()).isEqualTo("망고");
        assertThat(dog.getBreed()).isEqualTo("골든 리트리버");
        assertThat(dog.getTemperamentTags()).containsExactly("차분해요", "친구를 좋아해요");
        assertThat(dog.getGender()).isEqualTo("MALE");
        assertThat(dog.getNeutered()).isTrue();
        assertThat(dog.getIntroduction()).isEqualTo("친구를 좋아해요");
        assertThat(dog.getLeashGreeting()).isEqualTo("LIKES");
        assertThat(dog.isDefault()).isTrue();

        dog.updateProfile(
                "망고2", "리트리버", LocalDate.of(2022, 6, 13), "", List.of("활발해요"),
                "FEMALE", false, " ", "DIFFICULT", "LIKES", "CONDITIONAL", "FREQUENT", "PRESENT"
        );
        assertThat(dog.getName()).isEqualTo("망고2");
        assertThat(dog.getProfileImageUrl()).isNull();
        assertThat(dog.getTemperamentTags()).containsExactly("활발해요");
        assertThat(dog.getGender()).isEqualTo("FEMALE");
        assertThat(dog.getNeutered()).isFalse();
        assertThat(dog.getIntroduction()).isNull();
        assertThat(dog.getBarkingLevel()).isEqualTo("FREQUENT");
        assertThat(dog.getBitingLevel()).isEqualTo("PRESENT");
    }

    @Test
    void softDeleteClearsDefaultAndPreventsFurtherChanges() {
        DogProfile dog = DogProfile.create(
                AppUser.register("owner@example.com", "보호자", "encoded", "01012345678"),
                "망고", "리트리버", LocalDate.of(2022, 5, 12), null, List.of(),
                "UNKNOWN", null, null, "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", true
        );

        dog.softDelete(OffsetDateTime.parse("2026-08-18T10:00:00+09:00"));

        assertThat(dog.isDeleted()).isTrue();
        assertThat(dog.isDefault()).isFalse();
        assertThatThrownBy(() -> dog.changeDefault(true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsFutureBirthDate() {
        assertThatThrownBy(() -> DogProfile.create(
                AppUser.register("owner@example.com", "보호자", "encoded", "01012345678"),
                "망고", "리트리버", LocalDate.now().plusDays(1), null, List.of(),
                "UNKNOWN", null, null, "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", false
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMoreThanFiveTemperamentTags() {
        assertThatThrownBy(() -> DogProfile.create(
                AppUser.register("owner@example.com", "보호자", "encoded", "01012345678"),
                "망고", "리트리버", LocalDate.of(2022, 5, 12), null,
                List.of("1", "2", "3", "4", "5", "6"),
                "UNKNOWN", null, null, "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 5개");
    }
}
