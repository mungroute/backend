package com.mungroute.course.draw.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record SaveCustomCourseRequest(
        @NotBlank @Size(max = 100) String courseName,
        @NotNull @Size(min = 2, max = 20) List<@Valid DrawWaypointRequest> waypoints,
        boolean loop,
        boolean representative,
        Instant requestedAt
) {
    public SaveCustomCourseRequest {
        waypoints = waypoints == null ? null : List.copyOf(waypoints);
    }
}
