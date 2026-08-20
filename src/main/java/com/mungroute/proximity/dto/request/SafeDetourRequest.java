package com.mungroute.proximity.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SafeDetourRequest(
        @NotBlank @Size(max = 80) String requestId,
        @NotNull @Pattern(regexp = "NEW|APPROACHING|STEADY|LEAVING") String alertTrend,
        @NotEmpty @Size(max = 2_000) List<@Valid SafeDetourPointRequest> remainingRoute
) {
    public SafeDetourRequest {
        remainingRoute = remainingRoute == null ? null : List.copyOf(remainingRoute);
    }
}
