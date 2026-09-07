package com.mungroute.user.port;

import java.time.OffsetDateTime;

public interface UserSessionRevocationPort {
    void revokeAllActive(long userId, OffsetDateTime revokedAt);
}
