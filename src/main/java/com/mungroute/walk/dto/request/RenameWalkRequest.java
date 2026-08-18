package com.mungroute.walk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameWalkRequest(
        @NotBlank @Size(max = 80) String courseName
) {
}
