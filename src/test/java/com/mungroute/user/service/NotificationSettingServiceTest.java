package com.mungroute.user.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.domain.NotificationSetting;
import com.mungroute.user.dto.request.NotificationSettingRequest;
import com.mungroute.user.exception.UserErrorCode;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.user.repository.NotificationSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @Mock
    NotificationSettingRepository repository;

    @Mock
    AppUserRepository userRepository;

    @InjectMocks
    NotificationSettingService service;

    @Test
    void returnsAnExistingSettingWithoutTakingTheCreationLock() {
        NotificationSetting setting = NotificationSetting.createDefault(user());
        setting.update(false, true, false, true);
        when(repository.findById(7L)).thenReturn(Optional.of(setting));

        var response = service.get(7L);

        assertThat(response.serviceEnabled()).isFalse();
        assertThat(response.distanceEnabled()).isTrue();
        assertThat(response.meetEnabled()).isFalse();
        assertThat(response.groupEnabled()).isTrue();
        verify(userRepository, never()).findByIdForUpdate(7L);
        verify(repository, never()).save(any());
    }

    @Test
    void doubleChecksAfterLockingBeforeCreatingEnabledDefaults() {
        AppUser user = user();
        when(repository.findById(7L)).thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(repository.save(any(NotificationSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.get(7L);

        assertThat(response.serviceEnabled()).isTrue();
        assertThat(response.distanceEnabled()).isTrue();
        assertThat(response.meetEnabled()).isTrue();
        assertThat(response.groupEnabled()).isTrue();
        verify(repository, times(2)).findById(7L);
        verify(userRepository).findByIdForUpdate(7L);
        verify(repository).save(any(NotificationSetting.class));
    }

    @Test
    void updatesAnExistingSettingWithoutLockingTheWholeUser() {
        NotificationSetting setting = NotificationSetting.createDefault(user());
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(setting));

        var response = service.update(7L, new NotificationSettingRequest(true, false, true, false));

        assertThat(response.distanceEnabled()).isFalse();
        assertThat(response.groupEnabled()).isFalse();
        verify(userRepository, never()).findByIdForUpdate(7L);
        verify(repository, never()).save(any());
    }

    @Test
    void createsAndUpdatesWhileHoldingTheUserLock() {
        AppUser user = user();
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(repository.save(any(NotificationSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(7L, new NotificationSettingRequest(true, false, true, false));

        assertThat(response.serviceEnabled()).isTrue();
        assertThat(response.distanceEnabled()).isFalse();
        assertThat(response.meetEnabled()).isTrue();
        assertThat(response.groupEnabled()).isFalse();
        verify(userRepository).findByIdForUpdate(7L);
        verify(repository).save(any(NotificationSetting.class));
    }

    @Test
    void rejectsAUserThatCannotBeLocked() {
        when(repository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                404L,
                new NotificationSettingRequest(true, true, true, true)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verify(repository, never()).save(any());
    }

    private AppUser user() {
        return AppUser.register(
                "notification-service@example.com",
                "알림서비스",
                "encoded-password",
                "01011112222"
        );
    }
}
