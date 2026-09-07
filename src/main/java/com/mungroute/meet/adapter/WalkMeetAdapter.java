package com.mungroute.meet.adapter;

import com.mungroute.meet.service.MeetService;
import com.mungroute.walk.port.WalkMeetPort;
import org.springframework.stereotype.Component;

@Component
public class WalkMeetAdapter implements WalkMeetPort {
    private final MeetService meetService;

    public WalkMeetAdapter(MeetService meetService) {
        this.meetService = meetService;
    }

    @Override
    public void closeForSession(long userId, long sessionId) {
        meetService.closeForSession(userId, sessionId);
    }
}
