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
        CourseSegmentData first = segments.getFirst();
        CourseSegmentData second = segments.get(1);
        long shared = sharedNode(first, second);
        long start = otherNode(first, shared);
        List<OrientedSegment> result = new ArrayList<>();
        result.add(new OrientedSegment(first, start, shared));
        long cursor = shared;
        for (int index = 1; index < segments.size(); index++) {
            CourseSegmentData segment = segments.get(index);
            if (segment.source() != cursor && segment.target() != cursor) {
                throw disconnected("코스 링크 순서가 연결되어 있지 않습니다.");
            }
            long next = otherNode(segment, cursor);
            result.add(new OrientedSegment(segment, cursor, next));
            cursor = next;
        }
        return result;
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
        List<Long> ids = slice.stream().map(value -> value.segment().segmentId()).toList();
        BigDecimal length = slice.stream()
                .map(value -> value.segment().lengthM())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CourseSection(
                index,
                from,
                to,
                slice.getFirst().from(),
                slice.getLast().to(),
                ids,
                length
        );
    }

    private long sharedNode(CourseSegmentData left, CourseSegmentData right) {
        Set<Long> leftNodes = new HashSet<>();
        leftNodes.add(left.source());
        leftNodes.add(left.target());
        if (leftNodes.contains(right.source())) {
            return right.source();
        }
        if (leftNodes.contains(right.target())) {
            return right.target();
        }
        throw disconnected("첫 두 코스 링크가 연결되어 있지 않습니다.");
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

    private record OrientedSegment(CourseSegmentData segment, long from, long to) {
    }
}
