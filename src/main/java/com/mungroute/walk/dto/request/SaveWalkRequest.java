package com.mungroute.walk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveWalkRequest(
        @NotBlank(message = "코스 이름은 필수입니다.")
        @Size(max = 100, message = "코스 이름은 100자 이하여야 합니다.")
        String courseName,
        boolean representative
) {
}
