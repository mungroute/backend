package com.mungroute.proximity.dto.response;

public record NearbyPresenceResponse(
        String distanceBand,
        Integer directionOctant,
        Integer directionSpread,
        String directionReference,
        String trend
) {
}
