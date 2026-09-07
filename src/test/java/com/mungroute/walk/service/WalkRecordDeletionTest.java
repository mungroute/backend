package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.domain.AppUser;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkRecordQueryRepository;
import com.mungroute.walk.repository.WalkSessionRepository;
import com.mungroute.walk.port.WalkSharingReferencePort;
import com.mungroute.walk.port.WalkOwnerLockPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalkRecordDeletionTest {
    @Mock WalkSessionRepository walkSessionRepository;
    @Mock WalkRecordQueryRepository queryRepository;
    @Mock ObjectMapper objectMapper;
    @Mock WalkSharingReferencePort sharingReferencePort;
    @Mock WalkOwnerLockPort ownerLockPort;
    @Mock WalkSession session;
    @Mock AppUser owner;
    @InjectMocks WalkRecordService service;

    @Test
    void blocksDeletionWhileTheWalkCourseIsSharedWithAGroup() {
        when(walkSessionRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(owner);
        when(owner.getUserId()).thenReturn(7L);
        when(session.isSaved()).thenReturn(true);
        when(sharingReferencePort.isShared(31L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L, 31L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WalkErrorCode.WALK_COURSE_SHARED_WITH_GROUP));
        verify(walkSessionRepository, never()).delete(session);
    }
}
