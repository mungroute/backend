package com.mungroute.course.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SegmentSwapEvaluator {
    private static final BigDecimal METERS_PER_MINUTE = new BigDecimal("40.0");
    private static final BigDecimal TARGET_TIME_TOLERANCE = new BigDecimal("0.15");

    public boolean withinTargetTime(BigDecimal lengthM, int targetTimeMin) {
        BigDecimal targetLength = METERS_PER_MINUTE.multiply(BigDecimal.valueOf(targetTimeMin));
        BigDecimal tolerance = targetLength.multiply(TARGET_TIME_TOLERANCE);
        return lengthM.subtract(targetLength).abs().compareTo(tolerance) <= 0;
    }

    public boolean withinOverallDetour(BigDecimal baseLength, BigDecimal alternativeLength, double ratio) {
        BigDecimal maximum = baseLength.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(ratio)));
        return alternativeLength.compareTo(maximum) <= 0;
    }

    public boolean overlaps(List<? extends Collection<Long>> candidates) {
        Set<Long> seen = new HashSet<>();
        for (Collection<Long> candidate : candidates) {
            for (Long segmentId : candidate) {
                if (!seen.add(segmentId)) return true;
            }
        }
        return false;
    }

    public boolean samePath(List<Long> left, List<Long> right) {
        if (left.equals(right)) return true;
        List<Long> reversed = new ArrayList<>(right);
        java.util.Collections.reverse(reversed);
        return left.equals(reversed);
    }

    public boolean intersects(Collection<Long> left, Set<Long> right) {
        return left.stream().anyMatch(right::contains);
    }
}
