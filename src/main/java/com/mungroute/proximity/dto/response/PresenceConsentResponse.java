package com.mungroute.proximity.dto.response;

import java.time.OffsetDateTime;

public record PresenceConsentResponse(
        Long sessionId,
        String mode,
        OffsetDateTime consentedAt
) {
}