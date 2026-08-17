package com.mungroute.course.draw.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ConnectCourseRequest(
        @NotNull @Size(min = 2, max = 20) List<@jakarta.validation.Valid DrawWaypointRequest> waypoints,
        Instant requestedAt
) {
    public ConnectCourseRequest {
        waypoints = waypoints == null ? null : List.copyOf(waypoints);
    }
}
