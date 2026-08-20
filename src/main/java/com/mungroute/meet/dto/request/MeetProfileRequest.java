package com.mungroute.meet.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MeetProfileRequest(
        @NotBlank @Size(max = 50) String dogName,
        @NotBlank @Size(max = 80) String breed,
        @Max(40) Integer ageYears,
        @Size(max = 2_000_000) String profileImageUrl,
        @Size(max = 8) List<@NotBlank @Size(max = 30) String> temperamentTags,
        @Pattern(regexp = "LIKES|NEUTRAL|DIFFICULT|UNKNOWN") String leashGreeting,
        @Pattern(regexp = "LIKES|NEUTRAL|DIFFICULT|UNKNOWN") String strangerResponse,
        @Pattern(regexp = "COMFORTABLE|CONDITIONAL|DIFFICULT|UNKNOWN") String touchTolerance,
        @Pattern(regexp = "RARE|NORMAL|FREQUENT|UNKNOWN") String barkingLevel,
        @Pattern(regexp = "NONE|CONDITIONAL|PRESENT|UNKNOWN") String bitingLevel
) {
    public MeetProfileRequest {
        leashGreeting = defaultValue(leashGreeting);
        strangerResponse = defaultValue(strangerResponse);
        touchTolerance = defaultValue(touchTolerance);
        barkingLevel = defaultValue(barkingLevel);
        bitingLevel = defaultValue(bitingLevel);
    }

    private static String defaultValue(String value) {
        return value == null ? "UNKNOWN" : value;
    }
}
