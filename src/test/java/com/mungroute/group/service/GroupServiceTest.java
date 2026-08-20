package com.mungroute.group.service;

import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.service.CourseCatalogService;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.group.dto.request.CreateGroupRequest;
import com.mungroute.group.dto.request.ShareCourseRequest;
import com.mungroute.group.dto.request.UpdateGroupRequest;
import com.mungroute.group.exception.GroupErrorCode;
import com.mungroute.group.repository.GroupInviteRow;
import com.mungroute.group.repository.GroupRepository;
import com.mungroute.group.repository.GroupSummaryRow;
import com.mungroute.group.repository.SharedCourseRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T05:00:00Z");

    @Mock
    GroupRepository groupRepository;

    @Mock
    CourseCatalogService courseCatalogService;

    @InjectMocks
    GroupService groupService;

    @Test
    void createsAGroupWithTheCreatorAsOwner() {
        when(groupRepository.create(1L, "남산 산책단", "함께 걸어요", "PRIVATE", "INVITE_ONLY"))
                .thenReturn(10L);
        when(groupRepository.findForMember(1L, 10L)).thenReturn(Optional.of(group("OWNER")));
        when(groupRepository.findMembers(10L)).thenReturn(List.of());
        when(groupRepository.findSharedCourses(10L, 3)).thenReturn(List.of());

        var created = groupService.create(1L, new CreateGroupRequest(" 남산 산책단 ", " 함께 걸어요 "), NOW);

        assertThat(created.groupId()).isEqualTo(10L);
        assertThat(created.myRole()).isEqualTo("OWNER");
        verify(groupRepository).addMember(10L, 1L, "OWNER");
        verify(groupRepository).addActivity(10L, 1L, "GROUP_CREATED", "남산 산책단");
    }

    @Test
    void blocksMemberOnlyAndOwnerOnlyBoundaries() {
        when(groupRepository.findForMember(2L, 10L)).thenReturn(Optional.of(group("MEMBER")));
        when(groupRepository.findRole(1L, 10L)).thenReturn(Optional.of("OWNER"));
        when(groupRepository.existsActive(10L)).thenReturn(true);

        assertThatThrownBy(() -> groupService.update(
                2L, 10L, new UpdateGroupRequest("수정", "설명"), NOW
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_OWNER_REQUIRED));

        assertThatThrownBy(() -> groupService.leave(1L, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_OWNER_LEAVE_NOT_ALLOWED));
        verify(groupRepository, never()).removeMember(anyLong(), anyLong());
    }

    @Test
    void joinsOnlyWithAUsableInviteAndRejectsDuplicateMembership() {
        when(groupRepository.findUsableInvite(eq("MUNG24"), any())).thenReturn(Optional.of(
                new GroupInviteRow(1L, 10L, "남산 산책단", "MUNG24", OffsetDateTime.now().plusDays(1))
        ));
        when(groupRepository.findRole(2L, 10L)).thenReturn(Optional.of("MEMBER"));

        assertThatThrownBy(() -> groupService.join(2L, "mung24", NOW))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_ALREADY_JOINED));
    }

    @Test
    void removesCoursesSharedByAnEvictedMember() {
        when(groupRepository.findForMember(1L, 10L)).thenReturn(Optional.of(group("OWNER")));
        when(groupRepository.removeMember(10L, 2L)).thenReturn(1);

        groupService.removeMember(1L, 10L, 2L);

        verify(groupRepository).deleteSharedCoursesByUser(10L, 2L);
        verify(groupRepository).addActivity(10L, 1L, "MEMBER_REMOVED", "2");
    }

    @Test
    void removesCoursesSharedByAMemberWhoLeaves() {
        when(groupRepository.existsActive(10L)).thenReturn(true);
        when(groupRepository.findRole(2L, 10L)).thenReturn(Optional.of("MEMBER"));
        when(groupRepository.removeMember(10L, 2L)).thenReturn(1);

        groupService.leave(2L, 10L);

        verify(groupRepository).deleteSharedCoursesByUser(10L, 2L);
    }

    @Test
    void joinsAPublicOpenGroupWithoutAnInvite() {
        when(groupRepository.findRole(2L, 10L)).thenReturn(Optional.empty());
        when(groupRepository.existsActive(10L)).thenReturn(true);
        when(groupRepository.isPublicOpen(10L)).thenReturn(true);
        when(groupRepository.addMember(10L, 2L, "MEMBER")).thenReturn(1);
        when(groupRepository.findForMember(2L, 10L)).thenReturn(Optional.of(group("MEMBER")));
        when(groupRepository.findMembers(10L)).thenReturn(List.of());
        when(groupRepository.findSharedCourses(10L, 3)).thenReturn(List.of());

        var joined = groupService.joinOpen(2L, 10L, NOW);

        assertThat(joined.myRole()).isEqualTo("MEMBER");
        verify(groupRepository).addActivity(10L, 2L, "MEMBER_JOINED", null);
    }

    @Test
    void sharesOnlyAnOwnedCourseAndAllowsOwnerOrSharerToCancel() {
        when(groupRepository.findForMember(2L, 10L)).thenReturn(Optional.of(group("MEMBER")));
        when(courseCatalogService.detail(2L, "custom", 7L, NOW)).thenReturn(course(7L));
        when(groupRepository.shareCourse(10L, 2L, "custom", 7L)).thenReturn(99L);
        when(groupRepository.findSharedCourse(10L, 99L)).thenReturn(Optional.of(
                new SharedCourseRow(99L, 10L, 2L, "쿠키 보호자", "custom", 7L, 0, OffsetDateTime.now())
        ));

        var shared = groupService.shareCourse(2L, 10L, new ShareCourseRequest("CUSTOM", 7L), NOW);

        assertThat(shared.sharedCourseId()).isEqualTo(99L);
        verify(groupRepository).addActivity(10L, 2L, "COURSE_SHARED", "남산길");
    }

    @Test
    void blocksResharingACourseThatWasSavedFromAGroup() {
        when(groupRepository.findForMember(2L, 10L)).thenReturn(Optional.of(group("MEMBER")));
        when(courseCatalogService.detail(2L, "custom", 7L, NOW)).thenReturn(course(7L));
        when(groupRepository.isGroupSavedCourse(7L)).thenReturn(true);

        assertThatThrownBy(() -> groupService.shareCourse(
                2L, 10L, new ShareCourseRequest("CUSTOM", 7L), NOW
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_SAVED_COURSE_RESHARE_NOT_ALLOWED));

        verify(groupRepository, never()).shareCourse(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void issuesSixCharacterInviteAndRevokesThePreviousOne() {
        when(groupRepository.findForMember(1L, 10L)).thenReturn(Optional.of(group("OWNER")));
        when(groupRepository.createInvite(eq(10L), eq(1L), any(), any())).thenReturn(1L);

        var invite = groupService.issueInvite(1L, 10L);

        assertThat(invite.inviteCode()).hasSize(6).containsPattern("[A-Z2-9]{6}");
        verify(groupRepository).revokeInvites(eq(10L), any());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(groupRepository).createInvite(eq(10L), eq(1L), code.capture(), any());
        assertThat(code.getValue()).isEqualTo(invite.inviteCode());
    }

    private GroupSummaryRow group(String role) {
        return new GroupSummaryRow(
                10L, "남산 산책단", "함께 걸어요", "PRIVATE", "INVITE_ONLY", 1L, role,
                2, 1, OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    private CourseDetailResponse course(long id) {
        return new CourseDetailResponse(
                "custom", id, "남산길", false, false, OffsetDateTime.now(),
                List.of(1L), null, null
        );
    }
}
