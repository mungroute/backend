package com.mungroute.proximity.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PresenceUpdateRequest(
        @Positive long sessionId,
        @NotNull OffsetDateTime measuredAt,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lon,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
        @NotNull @DecimalMin("0.0") @DecimalMax("40.0") BigDecimal accuracy,
        @DecimalMin("0.0") @DecimalMax(value = "360.0", inclusive = false) BigDecimal heading,
        boolean stationary,
        @Min(50) @Max(500) int radiusM
) {
}
