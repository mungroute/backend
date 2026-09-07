package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.port.CourseOwnerLockPort;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.domain.CourseSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRepresentativeServiceTest {
    @Mock CourseCatalogRepository repository;
    @Mock CourseOwnerLockPort ownerLockPort;

    @Test
    void locksTheOwnerBeforeClearingAndSettingARepresentative() {
        CourseCatalogRow row = mock(CourseCatalogRow.class);
        when(ownerLockPort.lock(7L)).thenReturn(true);
        when(repository.findOwned(7L, CourseSource.CUSTOM, 42L)).thenReturn(Optional.of(row));
        when(repository.setRepresentative(7L, CourseSource.CUSTOM, 42L, true)).thenReturn(1);

        CourseCatalogRow result = new CourseRepresentativeService(repository, ownerLockPort)
                .setRepresentative(7L, CourseSource.CUSTOM, 42L, true);

        assertThat(result).isSameAs(row);
        InOrder order = inOrder(ownerLockPort, repository);
        order.verify(ownerLockPort).lock(7L);
        order.verify(repository).findOwned(7L, CourseSource.CUSTOM, 42L);
        order.verify(repository).clearRepresentatives(7L);
        order.verify(repository).setRepresentative(7L, CourseSource.CUSTOM, 42L, true);
    }
}
