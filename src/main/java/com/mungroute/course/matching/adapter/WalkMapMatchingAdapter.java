package com.mungroute.course.matching.adapter;

import com.mungroute.course.matching.MapMatchingResult;
import com.mungroute.course.matching.SimpleMapMatchingService;
import com.mungroute.walk.port.WalkMapMatchingPort;
import org.springframework.stereotype.Component;

@Component
public class WalkMapMatchingAdapter implements WalkMapMatchingPort {
    private final SimpleMapMatchingService mapMatchingService;

    public WalkMapMatchingAdapter(SimpleMapMatchingService mapMatchingService) {
        this.mapMatchingService = mapMatchingService;
    }

    @Override
    public MapMatchingResult matchSession(long sessionId) {
        return mapMatchingService.matchSession(sessionId);
    }
}
