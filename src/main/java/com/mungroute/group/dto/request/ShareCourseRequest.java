package com.mungroute.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ShareCourseRequest(
        @NotBlank @Pattern(regexp = "(?i)walk|custom") String courseSource,
        @Positive long courseId
) {
}
