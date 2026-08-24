package com.mungroute.course.service;

import com.mungroute.course.domain.CourseSection;
import com.mungroute.course.domain.CourseSegmentData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseSectionSplitterTest {
    private final CourseSectionSplitter splitter = new CourseSectionSplitter();

    @Test
    void createsThreeSectionsWhenCourseHasNoIntersectionBoundaries() {
        List<CourseSection> sections = splitter.split(segments(6), Map.of());

        assertThat(sections).hasSize(3);
        assertThat(sections).extracting(CourseSection::segmentIds)
                .containsExactly(List.of(1L, 2L), List.of(3L, 4L), List.of(5L, 6L));
        assertThat(sections).extracting(CourseSection::startNode)
                .containsExactly(1L, 3L, 5L);
        assertThat(sections).extracting(CourseSection::endNode)
                .containsExactly(3L, 5L, 7L);
    }

    @Test
    void mergesIntersectionSectionsDownToFive() {
        Map<Long, Integer> degrees = new HashMap<>();
        LongStream.rangeClosed(2, 7).forEach(node -> degrees.put(node, 3));

        List<CourseSection> sections = splitter.split(segments(7), degrees);

        assertThat(sections).hasSize(5);
        assertThat(sections.stream().flatMap(section -> section.segmentIds().stream()).toList())
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    @Test
    void rejectsDisconnectedSequence() {
        List<CourseSegmentData> broken = List.of(segment(1, 1, 2), segment(2, 9, 10), segment(3, 10, 11));

        assertThatThrownBy(() -> splitter.split(broken, Map.of()))
                .isInstanceOf(CourseProcessingException.class);
    }

    @Test
    void keepsCourseConnectedWhenSameSegmentIsSplitAtWaypointBoundary() {
        List<CourseSegmentData> waypointSplitCourse = List.of(
                segment(45395, 1_000, 1_691),
                segment(105789, 1_691, 1_739),
                segment(105789, 1_691, 1_739),
                segment(59369, 1_739, 1_745)
        );

        List<CourseSection> sections = splitter.split(waypointSplitCourse, Map.of());

        assertThat(sections.stream().flatMap(section -> section.segmentIds().stream()).toList())
                .containsExactly(45395L, 105789L, 105789L, 59369L);
        assertThat(sections.getFirst().startNode()).isEqualTo(1_000L);
        assertThat(sections.getLast().endNode()).isEqualTo(1_745L);
    }

    @Test
    void preservesARealOutAndBackWhenTheFullEdgeInterpretationIsConnected() {
        List<CourseSegmentData> outAndBackCourse = List.of(
                segment(10, 1, 2),
                segment(20, 2, 3),
                segment(20, 2, 3),
                segment(30, 2, 4)
        );

        List<CourseSection> sections = splitter.split(outAndBackCourse, Map.of());

        assertThat(sections.stream().flatMap(section -> section.segmentIds().stream()).toList())
                .containsExactly(10L, 20L, 20L, 30L);
        assertThat(sections.getFirst().startNode()).isEqualTo(1L);
        assertThat(sections.getLast().endNode()).isEqualTo(4L);
        assertThat(sections.getLast().segmentIds()).containsExactly(20L, 30L);
        assertThat(sections.getLast().startNode()).isEqualTo(3L);
        assertThat(sections.getLast().fromSegmentIndex()).isEqualTo(2);
    }

    @Test
    void doesNotGuessSplitDirectionForDuplicateSegmentsAtCourseBoundary() {
        List<CourseSegmentData> ambiguousBoundary = List.of(
                segment(20, 2, 3),
                segment(20, 2, 3),
                segment(30, 3, 4)
        );

        assertThatThrownBy(() -> splitter.split(ambiguousBoundary, Map.of()))
                .isInstanceOf(CourseProcessingException.class)
                .hasMessageContaining("방향을 확정할 수 없습니다");
    }

    private List<CourseSegmentData> segments(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> segment(id, id, id + 1))
                .toList();
    }

    private CourseSegmentData segment(long id, long source, long target) {
        return new CourseSegmentData(
                id, source, target,
                new BigDecimal("100"),
                new BigDecimal("0.2"),
                new BigDecimal("40"),
                "LOW",
                LocalDate.of(2026, 8, 11)
        );
    }
}
