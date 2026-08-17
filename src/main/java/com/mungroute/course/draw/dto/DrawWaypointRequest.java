package com.mungroute.course.draw.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DrawWaypointRequest(
        @NotNull @Valid DrawPointRequest original,
        @NotNull @Valid DrawPointRequest snapped,
        @NotNull @Positive Long nodeId,
        @NotNull @Positive Long segmentId,
        boolean fallbackApplied
) {
}
