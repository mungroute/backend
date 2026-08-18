package com.mungroute.walk.dto.response;

import java.util.List;

public record WalkContributionsResponse(
        int year,
        Long dogId,
        List<WalkContributionDayResponse> days
) {
}
