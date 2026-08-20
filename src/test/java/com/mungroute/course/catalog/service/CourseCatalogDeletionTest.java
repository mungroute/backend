package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.course.service.SegmentSwapService;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.group.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

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
    @Mock CourseRoutingRepository routingRepository;
    @Mock CourseMetricsCalculator metricsCalculator;
    @Mock SegmentSwapService segmentSwapService;
    @Mock SolarPositionService solarPositionService;
    @Mock ObjectMapper objectMapper;
    @Mock GroupRepository groupRepository;
    @InjectMocks CourseCatalogService service;

    @Test
    void blocksDeletionWhileTheCourseIsSharedWithAGroup() {
        when(catalogRepository.findOwned(7L, com.mungroute.course.domain.CourseSource.CUSTOM, 42L))
                .thenReturn(Optional.of(mock(CourseCatalogRow.class)));
        when(groupRepository.isCourseShared("custom", 42L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L, "custom", 42L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CourseCatalogErrorCode.COURSE_SHARED_WITH_GROUP));
        verify(catalogRepository, never()).deleteOwned(7L, com.mungroute.course.domain.CourseSource.CUSTOM, 42L);
    }
}
