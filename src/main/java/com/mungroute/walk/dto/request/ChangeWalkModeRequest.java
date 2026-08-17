package com.mungroute.walk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeWalkModeRequest(
        @NotBlank(message = "산책 모드는 필수입니다.")
        @Pattern(
                regexp = "off|distance|meet",
                message = "산책 모드는 off, distance, meet 중 하나여야 합니다."
        )
        String mode
) {
}
