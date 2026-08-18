package com.mungroute.walk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

// 산책 시작 요청 DTO
public record StartWalkRequest (
        @NotBlank(message = "산책 모드는 필수입니다.")
        @Pattern(
                regexp = "off|distance|meet",
                message = "산책 모드는 off, distance, meet 중 하나여야 합니다."
        )
        String mode,
        @Size(max = 5, message = "한 산책에는 반려견을 최대 5마리까지 선택할 수 있습니다.")
        List<@Positive Long> dogIds
){
    public StartWalkRequest {
        dogIds = dogIds == null ? List.of() : List.copyOf(dogIds);
    }

    public StartWalkRequest(String mode) {
        this(mode, List.of());
    }
}
