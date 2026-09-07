package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.port.CourseOwnerLockPort;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseRepresentativeService {
    private final CourseCatalogRepository catalogRepository;
    private final CourseOwnerLockPort ownerLockPort;

    public CourseRepresentativeService(
            CourseCatalogRepository catalogRepository,
            CourseOwnerLockPort ownerLockPort
    ) {
        this.catalogRepository = catalogRepository;
        this.ownerLockPort = ownerLockPort;
    }

    @Transactional
    public CourseCatalogRow setRepresentative(
            long userId,
            CourseSource source,
            long courseId,
            boolean representative
    ) {
        if (!ownerLockPort.lock(userId)) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND);
        }
        CourseCatalogRow row = owned(userId, source, courseId);
        if (representative && source == CourseSource.WALK && (!row.loop() || row.segmentIds().isEmpty())) {
            throw new BusinessException(CourseCatalogErrorCode.REPRESENTATIVE_COURSE_INELIGIBLE);
        }
        if (representative) {
            catalogRepository.clearRepresentatives(userId);
        }
        if (catalogRepository.setRepresentative(userId, source, courseId, representative) != 1) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND);
        }
        return owned(userId, source, courseId);
    }

    private CourseCatalogRow owned(long userId, CourseSource source, long courseId) {
        return catalogRepository.findOwned(userId, source, courseId)
                .orElseThrow(() -> new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND));
    }
}
