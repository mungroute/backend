package com.mungroute.meet.repository;

import java.util.List;

public record MeetProfileRecord(
        long userId,
        String dogName,
        String breed,
        Integer ageYears,
        String profileImageUrl,
        List<String> temperamentTags
) {
}
