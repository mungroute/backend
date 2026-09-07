package com.mungroute.group.adapter;

import com.mungroute.course.catalog.port.CourseSharingReferencePort;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.group.repository.GroupRepository;
import com.mungroute.walk.port.WalkSharingReferencePort;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CourseSharingReferenceAdapter implements CourseSharingReferencePort, WalkSharingReferencePort {
    private final GroupRepository groupRepository;

    public CourseSharingReferenceAdapter(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Override
    public boolean isShared(CourseSource source, long courseId) {
        return groupRepository.isCourseShared(source.name().toLowerCase(Locale.ROOT), courseId);
    }

    @Override
    public boolean isShared(long walkSessionId) {
        return groupRepository.isCourseShared("walk", walkSessionId);
    }
}
