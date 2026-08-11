package com.mungroute.walk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// 산책 시작 요청 DTO
public record StartWalkRequest (
        @NotNull(message = "사용자 ID는 필수입니다.")
        @Positive(message = "사용자 ID는 1 이상이어야 합니다.")
        Long userId,

        @NotBlank(message = "산책 모드는 필수입니다.")
        @Pattern(
                regexp = "off|distance|meet",
                message = "산책 모드는 off, distance, meet 중 하나여야 합니다."
        )
        String mode
){
}
