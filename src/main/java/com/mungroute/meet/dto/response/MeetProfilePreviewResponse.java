package com.mungroute.meet.dto.response;

import com.mungroute.meet.repository.MeetProfileRecord;

public record MeetProfilePreviewResponse(
        String profileImageUrl,
        String leashGreeting,
        String strangerResponse,
        String touchTolerance,
        String barkingLevel,
        String bitingLevel
) {
    public static MeetProfilePreviewResponse from(MeetProfileRecord profile) {
        return new MeetProfilePreviewResponse(
                profile.profileImageUrl(), profile.leashGreeting(), profile.strangerResponse(),
                profile.touchTolerance(), profile.barkingLevel(), profile.bitingLevel()
        );
    }
}
