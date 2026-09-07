package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.port.CourseSharingReferencePort;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCatalogDeletionTest {
    @Mock CourseCatalogRepository catalogRepository;
    @Mock CourseSharingReferencePort sharingReferencePort;
    @InjectMocks CourseDeletionService service;

    @Test
    void blocksDeletionWhileTheCourseIsSharedWithAGroup() {
        when(catalogRepository.findOwned(7L, com.mungroute.course.domain.CourseSource.CUSTOM, 42L))
                .thenReturn(Optional.of(mock(CourseCatalogRow.class)));
        when(sharingReferencePort.isShared(com.mungroute.course.domain.CourseSource.CUSTOM, 42L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L, com.mungroute.course.domain.CourseSource.CUSTOM, 42L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CourseCatalogErrorCode.COURSE_SHARED_WITH_GROUP));
        verify(catalogRepository, never()).deleteOwned(7L, com.mungroute.course.domain.CourseSource.CUSTOM, 42L);
    }
}
