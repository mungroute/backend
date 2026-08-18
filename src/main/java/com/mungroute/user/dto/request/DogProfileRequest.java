package com.mungroute.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record DogProfileRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 80) String breed,
        @NotNull @PastOrPresent LocalDate birthDate,
        @Size(max = 2_000_000) String profileImageUrl,
        @Size(max = 5, message = "성향 태그는 최대 5개까지 선택할 수 있습니다.")
        List<@NotBlank @Size(max = 30) String> temperamentTags,
        @Pattern(regexp = "MALE|FEMALE|UNKNOWN") String gender,
        Boolean neutered,
        @Size(max = 50) String introduction,
        @Pattern(regexp = "LIKES|NEUTRAL|DIFFICULT|UNKNOWN") String leashGreeting,
        @Pattern(regexp = "LIKES|NEUTRAL|DIFFICULT|UNKNOWN") String strangerResponse,
        @Pattern(regexp = "COMFORTABLE|CONDITIONAL|DIFFICULT|UNKNOWN") String touchTolerance,
        @Pattern(regexp = "RARE|NORMAL|FREQUENT|UNKNOWN") String barkingLevel,
        @Pattern(regexp = "NONE|CONDITIONAL|PRESENT|UNKNOWN") String bitingLevel,
        boolean isDefault
) {
    public DogProfileRequest {
        temperamentTags = temperamentTags == null ? List.of() : List.copyOf(temperamentTags);
        gender = defaultValue(gender);
        introduction = introduction == null || introduction.isBlank() ? null : introduction.trim();
        leashGreeting = defaultValue(leashGreeting);
        strangerResponse = defaultValue(strangerResponse);
        touchTolerance = defaultValue(touchTolerance);
        barkingLevel = defaultValue(barkingLevel);
        bitingLevel = defaultValue(bitingLevel);
    }

    private static String defaultValue(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
