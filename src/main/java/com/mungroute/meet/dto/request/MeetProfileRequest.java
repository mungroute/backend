package com.mungroute.meet.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MeetProfileRequest(
        @NotBlank @Size(max = 50) String dogName,
        @NotBlank @Size(max = 80) String breed,
        @Max(40) Integer ageYears,
        @Size(max = 500) String profileImageUrl,
        @Size(max = 8) List<@NotBlank @Size(max = 30) String> temperamentTags
) {
}
