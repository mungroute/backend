package com.mungroute.walk.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AddWalkPointRequest(
        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(
                value = "-90.0",
                message = "위도는 -90 이상이어야 합니다."
        )
        @DecimalMax(
                value = "90.0",
                message = "위도는 90 이하여야 합니다."
        )
        Double lat,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(
                value = "-180.0",
                message = "경도는 -180 이상이어야 합니다."
        )
        @DecimalMax(
                value = "180.0",
                message = "경도는 180 이하여야 합니다."
        )
        Double lon,

        @NotNull(message = "GPS 정확도는 필수입니다.")
        @DecimalMin(
                value = "0.0",
                message = "GPS 정확도는 0 이상이어야 합니다."
        )
        @DecimalMax(
                value = "9999.9",
                message = "GPS 정확도는 9999.9 이하여야 합니다."
        )
        BigDecimal accuracy,

        @NotNull(message = "GPS 기록 시각은 필수입니다.")
        OffsetDateTime recordedAt
) {
}
