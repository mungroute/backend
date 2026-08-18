package com.mungroute.group.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GroupRepository {
    List<GroupSummaryRow> findAllByMember(long userId);
    List<GroupSummaryRow> findDiscoverable(long userId);
    Optional<GroupSummaryRow> findForMember(long userId, long groupId);
    boolean existsActive(long groupId);
    boolean isPublicOpen(long groupId);
    Optional<String> findRole(long userId, long groupId);
    long create(long ownerUserId, String name, String description, String visibility, String joinPolicy);
    int addMember(long groupId, long userId, String role);
    int update(long groupId, String name, String description, String visibility, String joinPolicy);
    int softDelete(long groupId);
    int removeMember(long groupId, long userId);
    int deleteSharedCoursesByUser(long groupId, long userId);
    List<GroupMemberRow> findMembers(long groupId);

    void revokeInvites(long groupId, OffsetDateTime revokedAt);
    long createInvite(long groupId, long createdBy, String code, OffsetDateTime expiresAt);
    Optional<GroupInviteRow> findActiveInvite(long groupId, OffsetDateTime now);
    Optional<GroupInviteRow> findUsableInvite(String code, OffsetDateTime now);

    long shareCourse(long groupId, long userId, String source, long courseId);
    Optional<SharedCourseRow> findSharedCourse(long groupId, long sharedCourseId);
    List<SharedCourseRow> findSharedCourses(long groupId, int size);
    int deleteSharedCourse(long groupId, long sharedCourseId);
    boolean hasSavedCourse(long sharedCourseId, long userId);
    long copySharedCourse(long sharedCourseId, long userId);
    void recordCourseSave(long sharedCourseId, long userId, long savedCourseId);

    void addActivity(long groupId, Long actorUserId, String type, String subject);
    List<GroupActivityRow> findActivities(long groupId, Long before, int size);
}
