package com.mungroute.meet.dto.response;

import com.mungroute.meet.repository.MeetProfileRecord;

import java.util.List;

public record MeetProfileResponse(
        String dogName,
        String breed,
        Integer ageYears,
        String profileImageUrl,
        List<String> temperamentTags,
        String leashGreeting,
        String strangerResponse,
        String touchTolerance,
        String barkingLevel,
        String bitingLevel
) {
    public static MeetProfileResponse from(MeetProfileRecord profile) {
        return new MeetProfileResponse(profile.dogName(), profile.breed(), profile.ageYears(),
                profile.profileImageUrl(), profile.temperamentTags(), profile.leashGreeting(),
                profile.strangerResponse(), profile.touchTolerance(), profile.barkingLevel(), profile.bitingLevel());
    }
}
