package com.mungroute.course.catalog.port;

/** Serializes representative-course changes for one owner. */
public interface CourseOwnerLockPort {
    boolean lock(long userId);
}
