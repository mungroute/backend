package com.mungroute.user.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DogProfileResponse(
        long dogId,
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
        boolean isDefault,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
