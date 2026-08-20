package com.mungroute.group.service;

import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.service.CourseCatalogService;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.group.dto.request.CreateGroupRequest;
import com.mungroute.group.dto.request.ShareCourseRequest;
import com.mungroute.group.dto.request.UpdateGroupRequest;
import com.mungroute.group.dto.response.GroupActivityResponse;
import com.mungroute.group.dto.response.GroupDetailResponse;
import com.mungroute.group.dto.response.GroupMemberResponse;
import com.mungroute.group.dto.response.GroupSharedCourseResponse;
import com.mungroute.group.dto.response.GroupSummaryResponse;
import com.mungroute.group.dto.response.InviteCodeResponse;
import com.mungroute.group.dto.response.SavedSharedCourseResponse;
import com.mungroute.group.exception.GroupErrorCode;
import com.mungroute.group.repository.GroupActivityRow;
import com.mungroute.group.repository.GroupInviteRow;
import com.mungroute.group.repository.GroupRepository;
import com.mungroute.group.repository.GroupSummaryRow;
import com.mungroute.group.repository.SharedCourseRow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class GroupService {
    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_LENGTH = 6;
    private static final int INVITE_RETRIES = 8;

    private final GroupRepository groupRepository;
    private final CourseCatalogService courseCatalogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public GroupService(GroupRepository groupRepository, CourseCatalogService courseCatalogService) {
        this.groupRepository = groupRepository;
        this.courseCatalogService = courseCatalogService;
    }

    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> list(long userId) {
        return groupRepository.findAllByMember(userId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> discover(long userId) {
        return groupRepository.findDiscoverable(userId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse detail(long userId, long groupId, Instant requestedAt) {
        GroupSummaryRow group = memberGroup(userId, groupId);
        List<GroupMemberResponse> members = groupRepository.findMembers(groupId).stream()
                .map(member -> new GroupMemberResponse(
                        member.userId(), member.nickname(), member.profileImageUrl(), member.role(), member.joinedAt()
                )).toList();
        List<GroupSharedCourseResponse> recent = sharedCourses(groupId, requestedAt, 3);
        return new GroupDetailResponse(
                group.groupId(), group.name(), group.description(), group.visibility(), group.joinPolicy(),
                group.role(), group.memberCount(),
                group.sharedCourseCount(), group.latestActivityAt(), group.createdAt(), members, recent
        );
    }

    @Transactional
    public GroupDetailResponse create(long userId, CreateGroupRequest request, Instant requestedAt) {
        String name = request.name().trim();
        String description = normalizeDescription(request.description());
        String visibility = normalizeVisibility(request.visibility(), "PRIVATE");
        String joinPolicy = normalizeJoinPolicy(request.joinPolicy(), visibility, "INVITE_ONLY");
        long groupId = groupRepository.create(userId, name, description, visibility, joinPolicy);
        groupRepository.addMember(groupId, userId, "OWNER");
        groupRepository.addActivity(groupId, userId, "GROUP_CREATED", name);
        return detail(userId, groupId, requestedAt);
    }

    @Transactional
    public GroupDetailResponse update(long userId, long groupId, UpdateGroupRequest request, Instant requestedAt) {
        GroupSummaryRow current = owner(userId, groupId);
        String name = request.name().trim();
        String visibility = normalizeVisibility(request.visibility(), current.visibility());
        String joinPolicy = normalizeJoinPolicy(request.joinPolicy(), visibility, current.joinPolicy());
        if (groupRepository.update(
                groupId, name, normalizeDescription(request.description()), visibility, joinPolicy
        ) != 1) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        groupRepository.addActivity(groupId, userId, "GROUP_UPDATED", name);
        return detail(userId, groupId, requestedAt);
    }

    @Transactional
    public void delete(long userId, long groupId) {
        owner(userId, groupId);
        if (groupRepository.softDelete(groupId) != 1) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
    }

    @Transactional
    public InviteCodeResponse issueInvite(long userId, long groupId) {
        GroupSummaryRow group = owner(userId, groupId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        groupRepository.revokeInvites(groupId, now);
        OffsetDateTime expiresAt = now.plusDays(7);
        for (int attempt = 0; attempt < INVITE_RETRIES; attempt++) {
            String code = randomInviteCode();
            try {
                groupRepository.createInvite(groupId, userId, code, expiresAt);
                return new InviteCodeResponse(groupId, group.name(), code, expiresAt);
            } catch (DataIntegrityViolationException ignored) {
                // 전역 초대 코드 충돌이면 새 코드를 발급한다.
            }
        }
        throw new IllegalStateException("고유한 그룹 초대 코드를 생성하지 못했습니다.");
    }

    @Transactional(readOnly = true)
    public InviteCodeResponse currentInvite(long userId, long groupId) {
        GroupSummaryRow group = owner(userId, groupId);
        GroupInviteRow invite = groupRepository.findActiveInvite(groupId, OffsetDateTime.now(ZoneOffset.UTC))
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_INVITE_INVALID));
        return new InviteCodeResponse(groupId, group.name(), invite.inviteCode(), invite.expiresAt());
    }

    @Transactional
    public GroupDetailResponse join(long userId, String code, Instant requestedAt) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        GroupInviteRow invite = groupRepository.findUsableInvite(code.trim().toUpperCase(Locale.ROOT), now)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_INVITE_INVALID));
        if (groupRepository.findRole(userId, invite.groupId()).isPresent()) {
            throw new BusinessException(GroupErrorCode.GROUP_ALREADY_JOINED);
        }
        try {
            groupRepository.addMember(invite.groupId(), userId, "MEMBER");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(GroupErrorCode.GROUP_ALREADY_JOINED);
        }
        groupRepository.addActivity(invite.groupId(), userId, "MEMBER_JOINED", null);
        return detail(userId, invite.groupId(), requestedAt);
    }

    @Transactional
    public GroupDetailResponse joinOpen(long userId, long groupId, Instant requestedAt) {
        if (groupRepository.findRole(userId, groupId).isPresent()) {
            throw new BusinessException(GroupErrorCode.GROUP_ALREADY_JOINED);
        }
        if (!groupRepository.existsActive(groupId)) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupRepository.isPublicOpen(groupId)) {
            throw new BusinessException(GroupErrorCode.GROUP_OPEN_JOIN_NOT_ALLOWED);
        }
        try {
            groupRepository.addMember(groupId, userId, "MEMBER");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(GroupErrorCode.GROUP_ALREADY_JOINED);
        }
        groupRepository.addActivity(groupId, userId, "MEMBER_JOINED", null);
        return detail(userId, groupId, requestedAt);
    }

    @Transactional
    public void leave(long userId, long groupId) {
        String role = role(userId, groupId);
        if ("OWNER".equals(role)) {
            throw new BusinessException(GroupErrorCode.GROUP_OWNER_LEAVE_NOT_ALLOWED);
        }
        if (groupRepository.removeMember(groupId, userId) != 1) {
            throw new BusinessException(GroupErrorCode.GROUP_MEMBER_NOT_FOUND);
        }
        groupRepository.deleteSharedCoursesByUser(groupId, userId);
        groupRepository.addActivity(groupId, userId, "MEMBER_LEFT", null);
    }

    @Transactional
    public void removeMember(long ownerUserId, long groupId, long memberUserId) {
        owner(ownerUserId, groupId);
        if (ownerUserId == memberUserId || groupRepository.removeMember(groupId, memberUserId) != 1) {
            throw new BusinessException(GroupErrorCode.GROUP_MEMBER_NOT_FOUND);
        }
        groupRepository.deleteSharedCoursesByUser(groupId, memberUserId);
        groupRepository.addActivity(groupId, ownerUserId, "MEMBER_REMOVED", String.valueOf(memberUserId));
    }

    @Transactional
    public GroupSharedCourseResponse shareCourse(
            long userId,
            long groupId,
            ShareCourseRequest request,
            Instant requestedAt
    ) {
        memberGroup(userId, groupId);
        String source = normalizeSource(request.courseSource());
        CourseDetailResponse course = courseCatalogService.detail(userId, source, request.courseId(), requestedAt);
        if ("custom".equals(source) && groupRepository.isGroupSavedCourse(request.courseId())) {
            throw new BusinessException(GroupErrorCode.GROUP_SAVED_COURSE_RESHARE_NOT_ALLOWED);
        }
        long sharedId;
        try {
            sharedId = groupRepository.shareCourse(groupId, userId, source, request.courseId());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(GroupErrorCode.GROUP_COURSE_ALREADY_SHARED);
        }
        groupRepository.addActivity(groupId, userId, "COURSE_SHARED", course.courseName());
        return sharedCourse(groupId, sharedId, requestedAt);
    }

    @Transactional(readOnly = true)
    public List<GroupSharedCourseResponse> courses(
            long userId,
            long groupId,
            String sort,
            int size,
            Instant requestedAt
    ) {
        memberGroup(userId, groupId);
        List<GroupSharedCourseResponse> courses = new ArrayList<>(sharedCourses(groupId, requestedAt, size));
        if ("shortest".equalsIgnoreCase(sort)) {
            courses.sort(Comparator.comparingDouble(value -> value.course().metrics() == null
                    ? Double.MAX_VALUE : value.course().metrics().lengthM().doubleValue()));
        } else if ("shade".equalsIgnoreCase(sort)) {
            courses.sort(Comparator.comparingDouble((GroupSharedCourseResponse value) ->
                    value.course().metrics() == null || value.course().metrics().shadeRatio() == null
                            ? -1 : value.course().metrics().shadeRatio().doubleValue()).reversed());
        }
        return courses;
    }

    @Transactional(readOnly = true)
    public GroupSharedCourseResponse sharedCourse(long userId, long groupId, long sharedCourseId, Instant requestedAt) {
        memberGroup(userId, groupId);
        return sharedCourse(groupId, sharedCourseId, requestedAt);
    }

    @Transactional
    public void unshareCourse(long userId, long groupId, long sharedCourseId) {
        GroupSummaryRow group = memberGroup(userId, groupId);
        SharedCourseRow shared = groupRepository.findSharedCourse(groupId, sharedCourseId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_SHARED_COURSE_NOT_FOUND));
        if (shared.sharedByUserId() != userId && !"OWNER".equals(group.role())) {
            throw new BusinessException(GroupErrorCode.GROUP_SHARED_COURSE_ACCESS_DENIED);
        }
        String courseName = resolveCourse(shared, null).courseName();
        groupRepository.deleteSharedCourse(groupId, sharedCourseId);
        groupRepository.addActivity(groupId, userId, "COURSE_UNSHARED", courseName);
    }

    @Transactional
    public SavedSharedCourseResponse saveSharedCourse(long userId, long groupId, long sharedCourseId) {
        memberGroup(userId, groupId);
        SharedCourseRow shared = groupRepository.findSharedCourse(groupId, sharedCourseId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_SHARED_COURSE_NOT_FOUND));
        if (groupRepository.hasSavedCourse(sharedCourseId, userId)) {
            throw new BusinessException(GroupErrorCode.GROUP_COURSE_ALREADY_SAVED);
        }
        CourseDetailResponse source = resolveCourse(shared, null);
        long copiedId = groupRepository.copySharedCourse(sharedCourseId, userId);
        if (copiedId <= 0) throw new BusinessException(GroupErrorCode.GROUP_COURSE_SAVE_UNAVAILABLE);
        groupRepository.recordCourseSave(sharedCourseId, userId, copiedId);
        groupRepository.addActivity(groupId, userId, "COURSE_SAVED", source.courseName());
        return new SavedSharedCourseResponse(sharedCourseId, "custom", copiedId);
    }

    @Transactional(readOnly = true)
    public List<GroupActivityResponse> activities(long userId, long groupId, Long before, int size) {
        memberGroup(userId, groupId);
        return groupRepository.findActivities(groupId, before, size).stream().map(this::activity).toList();
    }

    private List<GroupSharedCourseResponse> sharedCourses(long groupId, Instant requestedAt, int size) {
        return groupRepository.findSharedCourses(groupId, size).stream()
                .map(row -> toSharedCourse(row, requestedAt))
                .toList();
    }

    private GroupSharedCourseResponse sharedCourse(long groupId, long sharedCourseId, Instant requestedAt) {
        SharedCourseRow row = groupRepository.findSharedCourse(groupId, sharedCourseId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_SHARED_COURSE_NOT_FOUND));
        return toSharedCourse(row, requestedAt);
    }

    private GroupSharedCourseResponse toSharedCourse(SharedCourseRow row, Instant requestedAt) {
        return new GroupSharedCourseResponse(
                row.sharedCourseId(), row.groupId(), row.sharedByUserId(), row.sharerNickname(),
                row.saveCount(), row.createdAt(), resolveCourse(row, requestedAt)
        );
    }

    private CourseDetailResponse resolveCourse(SharedCourseRow row, Instant requestedAt) {
        return courseCatalogService.detail(row.sharedByUserId(), row.courseSource(), row.courseId(), requestedAt);
    }

    private GroupActivityResponse activity(GroupActivityRow row) {
        String actor = row.actorNickname() == null ? "탈퇴한 멤버" : row.actorNickname();
        String message = switch (row.activityType()) {
            case "GROUP_CREATED" -> "그룹을 만들었어요";
            case "GROUP_UPDATED" -> "그룹 정보를 수정했어요";
            case "MEMBER_JOINED" -> "그룹에 참여했어요";
            case "MEMBER_LEFT" -> "그룹에서 나갔어요";
            case "MEMBER_REMOVED" -> "멤버를 내보냈어요";
            case "COURSE_SHARED" -> row.subject() + " 코스를 공유했어요";
            case "COURSE_UNSHARED" -> row.subject() + " 공유를 취소했어요";
            case "COURSE_SAVED" -> row.subject() + " 코스를 저장했어요";
            default -> "그룹에서 활동했어요";
        };
        return new GroupActivityResponse(
                row.activityId(), row.actorUserId(), actor, row.actorProfileImageUrl(),
                row.activityType(), row.subject(), message, row.createdAt()
        );
    }

    private GroupSummaryRow memberGroup(long userId, long groupId) {
        return groupRepository.findForMember(userId, groupId)
                .orElseThrow(() -> new BusinessException(
                        groupRepository.existsActive(groupId)
                                ? GroupErrorCode.GROUP_ACCESS_DENIED
                                : GroupErrorCode.GROUP_NOT_FOUND
                ));
    }

    private GroupSummaryRow owner(long userId, long groupId) {
        GroupSummaryRow group = memberGroup(userId, groupId);
        if (!"OWNER".equals(group.role())) throw new BusinessException(GroupErrorCode.GROUP_OWNER_REQUIRED);
        return group;
    }

    private String role(long userId, long groupId) {
        if (!groupRepository.existsActive(groupId)) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        return groupRepository.findRole(userId, groupId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_ACCESS_DENIED));
    }

    private GroupSummaryResponse summary(GroupSummaryRow row) {
        return new GroupSummaryResponse(
                row.groupId(), row.name(), row.description(), row.visibility(), row.joinPolicy(),
                row.role(), row.memberCount(),
                row.sharedCourseCount(), row.latestActivityAt(), row.createdAt()
        );
    }

    private String randomInviteCode() {
        StringBuilder code = new StringBuilder(INVITE_LENGTH);
        for (int index = 0; index < INVITE_LENGTH; index++) {
            code.append(INVITE_ALPHABET.charAt(secureRandom.nextInt(INVITE_ALPHABET.length())));
        }
        return code.toString();
    }

    private String normalizeSource(String source) {
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("walk") && !normalized.equals("custom")) {
            throw new BusinessException(GroupErrorCode.GROUP_COURSE_SAVE_UNAVAILABLE);
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
    }

    private String normalizeVisibility(String requested, String fallback) {
        String value = requested == null ? fallback : requested.trim().toUpperCase(Locale.ROOT);
        return "PUBLIC".equals(value) ? "PUBLIC" : "PRIVATE";
    }

    private String normalizeJoinPolicy(String requested, String visibility, String fallback) {
        if ("PRIVATE".equals(visibility)) return "INVITE_ONLY";
        String value = requested == null ? fallback : requested.trim().toUpperCase(Locale.ROOT);
        return "OPEN".equals(value) ? "OPEN" : "INVITE_ONLY";
    }
}
