package com.mungroute.proximity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PresenceConsentRequest (
        @NotNull(message = "산첵 세션 ID는 필수입니다.")
        @Positive(message = "산책 세션 ID는 1이상이어야 합니다.")
        Long sessionId
){
}
