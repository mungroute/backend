package com.mungroute.course.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record CourseRoutingPolicy(
        int candidateCount,
        double shadeAlpha,
        double sectionDetourRatio,
        int maxSwappedSections
) {
    public CourseRoutingPolicy(
            @Value("${course.routing.candidate-count:3}") int candidateCount,
            @Value("${course.routing.shade-alpha:2.0}") double shadeAlpha,
            @Value("${course.routing.section-detour-ratio:0.40}") double sectionDetourRatio,
            @Value("${course.routing.max-swapped-sections:2}") int maxSwappedSections
    ) {
        if (candidateCount < 1 || shadeAlpha < 0 || sectionDetourRatio < 0 || maxSwappedSections < 1) {
            throw new IllegalArgumentException("코스 탐색 정책값이 올바르지 않습니다.");
        }
        this.candidateCount = candidateCount;
        this.shadeAlpha = shadeAlpha;
        this.sectionDetourRatio = sectionDetourRatio;
        this.maxSwappedSections = maxSwappedSections;
    }
}
