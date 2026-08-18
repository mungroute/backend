package com.mungroute.proximity.store;

import java.util.List;

public record NearbyPresenceTransition(
        List<Long> enteredSessionIds,
        List<Long> leftSessionIds
) {
}
