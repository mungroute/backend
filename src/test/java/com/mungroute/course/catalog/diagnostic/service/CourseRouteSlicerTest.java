package com.mungroute.course.catalog.diagnostic.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRouteSlicerTest {
    private final CourseRouteSlicer slicer = new CourseRouteSlicer(new ObjectMapper());

    @Test
    void splitsOnlyInsideTheSavedCourseFromStartToFinish() {
        var slices = slicer.split("""
                {"type":"LineString","coordinates":[[126.0,37.0],[126.01,37.0],[126.02,37.0]]}
                """, List.of(new BigDecimal("25"), new BigDecimal("50"), new BigDecimal("25")));

        assertThat(slices).hasSize(3);
        assertThat(slices.getFirst().path("coordinates").get(0).get(0).asDouble()).isEqualTo(126.0);
        assertThat(slices.getLast().path("coordinates").get(1).get(0).asDouble()).isEqualTo(126.02);
        assertThat(slices.get(0).path("coordinates").get(1).get(0).asDouble()).isCloseTo(126.005, within(1e-8));
        assertThat(slices.get(1).path("coordinates").get(0).get(0).asDouble()).isCloseTo(126.005, within(1e-8));
        assertThat(slices.get(1).path("coordinates").get(2).get(0).asDouble()).isCloseTo(126.015, within(1e-8));
        assertThat(slices.get(2).path("coordinates").get(0).get(0).asDouble()).isCloseTo(126.015, within(1e-8));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
