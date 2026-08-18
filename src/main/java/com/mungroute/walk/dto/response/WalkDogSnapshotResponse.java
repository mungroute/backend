package com.mungroute.walk.dto.response;

public record WalkDogSnapshotResponse(
        long dogId,
        String name,
        String breed
) {
}
