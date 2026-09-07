package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.port.CourseSharingReferencePort;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseDeletionService {
    private final CourseCatalogRepository catalogRepository;
    private final CourseSharingReferencePort sharingReferencePort;

    public CourseDeletionService(
            CourseCatalogRepository catalogRepository,
            CourseSharingReferencePort sharingReferencePort
    ) {
        this.catalogRepository = catalogRepository;
        this.sharingReferencePort = sharingReferencePort;
    }

    @Transactional
    public void delete(long userId, CourseSource source, long courseId) {
        catalogRepository.findOwned(userId, source, courseId)
                .orElseThrow(() -> new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND));
        if (sharingReferencePort.isShared(source, courseId)) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_SHARED_WITH_GROUP);
        }
        if (catalogRepository.deleteOwned(userId, source, courseId) != 1) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND);
        }
    }
}
