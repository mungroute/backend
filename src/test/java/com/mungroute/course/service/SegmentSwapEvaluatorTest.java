package com.mungroute.course.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentSwapEvaluatorTest {
    private final SegmentSwapEvaluator evaluator = new SegmentSwapEvaluator();

    @Test
    void evaluatesTimeDetourAndTopologyConstraints() {
        assertThat(evaluator.withinTargetTime(new BigDecimal("600"), 15)).isTrue();
        assertThat(evaluator.withinTargetTime(new BigDecimal("700"), 15)).isFalse();
        assertThat(evaluator.withinOverallDetour(
                new BigDecimal("1000"), new BigDecimal("1150"), 0.15)).isTrue();
        assertThat(evaluator.withinOverallDetour(
                new BigDecimal("1000"), new BigDecimal("1151"), 0.15)).isFalse();
        assertThat(evaluator.samePath(List.of(1L, 2L), List.of(2L, 1L))).isTrue();
        assertThat(evaluator.intersects(List.of(2L, 3L), Set.of(1L, 2L))).isTrue();
        assertThat(evaluator.overlaps(List.of(List.of(1L, 2L), List.of(2L, 3L)))).isTrue();
    }
}
