package com.mungroute.course.service;

import com.mungroute.course.domain.AlternativeReason;
import com.mungroute.course.domain.CourseSection;
import com.mungroute.course.domain.CourseSegmentData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CourseSectionSplitter {
    private static final int MIN_SECTION_COUNT = 3;
    private static final int MAX_SECTION_COUNT = 5;

    public List<CourseSection> split(List<CourseSegmentData> orderedSegments, Map<Long, Integer> vertexDegrees) {
        if (orderedSegments == null || orderedSegments.size() < MIN_SECTION_COUNT
                || orderedSegments.stream().anyMatch(segment -> segment == null)) {
            throw disconnected("구간 치환에는 연결된 링크가 3개 이상 필요합니다.");
        }
        List<OrientedSegment> oriented = orient(orderedSegments);
        List<CourseSection> sections = splitAtIntersections(oriented, vertexDegrees);
        if (sections.size() < MIN_SECTION_COUNT) {
            sections = splitEvenly(oriented, MIN_SECTION_COUNT);
        }
        while (sections.size() > MAX_SECTION_COUNT) {
            sections = mergeShortest(sections);
        }
        return reindex(sections);
    }

    private List<OrientedSegment> orient(List<CourseSegmentData> segments) {
        if (sameUndirectedSegment(segments.getFirst(), segments.get(1))
                || sameUndirectedSegment(segments.get(segments.size() - 2), segments.getLast())) {
            throw disconnected("코스 경계의 반복 링크 방향을 확정할 수 없습니다.");
        }
        CourseSegmentData first = segments.getFirst();
        for (long start : List.of(first.source(), first.target())) {
            List<OrientedSegment> result = orientFrom(
                    segments,
                    0,
                    start,
                    new HashSet<>()
            );
            if (result != null) {
                return result;
            }
        }
        throw disconnected("코스 링크 순서가 연결되어 있지 않습니다.");
    }

    private List<OrientedSegment> orientFrom(
            List<CourseSegmentData> segments,
            int index,
            long cursor,
            Set<OrientationState> failed
    ) {
        if (index == segments.size()) {
            return new ArrayList<>();
        }
        OrientationState state = new OrientationState(index, cursor);
        if (failed.contains(state)) {
            return null;
        }

        CourseSegmentData segment = segments.get(index);
        if (!touches(segment, cursor)) {
            failed.add(state);
            return null;
        }
        long next = otherNode(segment, cursor);

        List<OrientedSegment> normallyOriented = orientFrom(segments, index + 1, next, failed);
        if (normallyOriented != null) {
            normallyOriented.addFirst(new OrientedSegment(
                    List.of(segment), index, index + 1, cursor, next
            ));
            return normallyOriented;
        }

        if (isInternalDuplicatePair(segments, index)) {
            List<OrientedSegment> splitThrough = orientFrom(segments, index + 2, next, failed);
            if (splitThrough != null) {
                splitThrough.addFirst(new OrientedSegment(
                        List.of(segment, segments.get(index + 1)),
                        index,
                        index + 2,
                        cursor,
                        next
                ));
                return splitThrough;
            }
        }

        failed.add(state);
        return null;
    }

    private List<CourseSection> splitAtIntersections(
            List<OrientedSegment> segments,
            Map<Long, Integer> vertexDegrees
    ) {
        List<CourseSection> result = new ArrayList<>();
        int sectionStart = 0;
        for (int index = 0; index < segments.size() - 1; index++) {
            long boundary = segments.get(index).to();
            if (vertexDegrees.getOrDefault(boundary, 0) >= 3) {
                result.add(section(result.size(), segments, sectionStart, index + 1));
                sectionStart = index + 1;
            }
        }
        result.add(section(result.size(), segments, sectionStart, segments.size()));
        return result;
    }

    private List<CourseSection> splitEvenly(List<OrientedSegment> segments, int targetCount) {
        List<CourseSection> result = new ArrayList<>();
        for (int part = 0; part < targetCount; part++) {
            int from = part * segments.size() / targetCount;
            int to = (part + 1) * segments.size() / targetCount;
            if (from < to) {
                result.add(section(result.size(), segments, from, to));
            }
        }
        return result;
    }

    private List<CourseSection> mergeShortest(List<CourseSection> sections) {
        int shortestIndex = 0;
        for (int index = 1; index < sections.size(); index++) {
            if (sections.get(index).lengthM().compareTo(sections.get(shortestIndex).lengthM()) < 0) {
                shortestIndex = index;
            }
        }
        int neighbor;
        if (shortestIndex == 0) {
            neighbor = 1;
        } else if (shortestIndex == sections.size() - 1) {
            neighbor = shortestIndex - 1;
        } else {
            neighbor = sections.get(shortestIndex - 1).lengthM()
                    .compareTo(sections.get(shortestIndex + 1).lengthM()) <= 0
                    ? shortestIndex - 1 : shortestIndex + 1;
        }
        int from = Math.min(shortestIndex, neighbor);
        int to = Math.max(shortestIndex, neighbor);
        CourseSection left = sections.get(from);
        CourseSection right = sections.get(to);
        List<Long> mergedIds = new ArrayList<>(left.segmentIds());
        mergedIds.addAll(right.segmentIds());
        CourseSection merged = new CourseSection(
                from,
                left.fromSegmentIndex(),
                right.toSegmentIndexExclusive(),
                left.startNode(),
                right.endNode(),
                mergedIds,
                left.lengthM().add(right.lengthM())
        );
        List<CourseSection> result = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            if (index == from) {
                result.add(merged);
            } else if (index != to) {
                result.add(sections.get(index));
            }
        }
        return result;
    }

    private List<CourseSection> reindex(List<CourseSection> sections) {
        List<CourseSection> result = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            CourseSection section = sections.get(index);
            result.add(new CourseSection(
                    index,
                    section.fromSegmentIndex(),
                    section.toSegmentIndexExclusive(),
                    section.startNode(),
                    section.endNode(),
                    section.segmentIds(),
                    section.lengthM()
            ));
        }
        return result;
    }

    private CourseSection section(List<CourseSection> existing, List<OrientedSegment> segments, int from, int to) {
        return section(existing.size(), segments, from, to);
    }

    private CourseSection section(int index, List<OrientedSegment> segments, int from, int to) {
        List<OrientedSegment> slice = segments.subList(from, to);
        List<Long> ids = slice.stream()
                .flatMap(value -> value.segments().stream())
                .map(CourseSegmentData::segmentId)
                .toList();
        BigDecimal length = slice.stream()
                .flatMap(value -> value.segments().stream())
                .map(CourseSegmentData::lengthM)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CourseSection(
                index,
                slice.getFirst().fromSegmentIndex(),
                slice.getLast().toSegmentIndexExclusive(),
                slice.getFirst().from(),
                slice.getLast().to(),
                ids,
                length
        );
    }

    private boolean touches(CourseSegmentData segment, long node) {
        return segment.source() == node || segment.target() == node;
    }

    private boolean sameUndirectedSegment(CourseSegmentData left, CourseSegmentData right) {
        return left.segmentId() == right.segmentId()
                && (left.source() == right.source() && left.target() == right.target()
                || left.source() == right.target() && left.target() == right.source());
    }

    private boolean isInternalDuplicatePair(List<CourseSegmentData> segments, int index) {
        if (index == 0 || index + 2 >= segments.size()) {
            return false;
        }
        CourseSegmentData current = segments.get(index);
        if (!sameUndirectedSegment(current, segments.get(index + 1))) {
            return false;
        }
        return !sameUndirectedSegment(segments.get(index - 1), current)
                && !sameUndirectedSegment(current, segments.get(index + 2));
    }

    private long otherNode(CourseSegmentData segment, long node) {
        if (segment.source() == node) {
            return segment.target();
        }
        if (segment.target() == node) {
            return segment.source();
        }
        throw disconnected("링크가 기대한 정점에 연결되어 있지 않습니다.");
    }

    private CourseProcessingException disconnected(String message) {
        return new CourseProcessingException(AlternativeReason.COURSE_NOT_CONNECTED, message);
    }

    private record OrientationState(int segmentIndex, long cursor) {
    }

    private record OrientedSegment(
            List<CourseSegmentData> segments,
            int fromSegmentIndex,
            int toSegmentIndexExclusive,
            long from,
            long to
    ) {
        private OrientedSegment {
            segments = List.copyOf(segments);
        }
    }
}
