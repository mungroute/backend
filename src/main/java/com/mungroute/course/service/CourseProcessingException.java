package com.mungroute.course.service;

import com.mungroute.course.domain.AlternativeReason;

public class CourseProcessingException extends RuntimeException {
    private final AlternativeReason reason;

    public CourseProcessingException(AlternativeReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AlternativeReason reason() {
        return reason;
    }
}
