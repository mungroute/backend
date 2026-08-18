package com.mungroute.meet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMeetRequest(
        @Positive long sessionId,
        @NotBlank @Size(max = 64) String candidateRef
) {
}
