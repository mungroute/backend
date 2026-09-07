package com.mungroute.auth.security;

import com.mungroute.auth.port.AuthUserPort;
import com.mungroute.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MungrouteUserDetailsServiceTest {

    @Mock
    AuthUserPort userPort;

    @InjectMocks
    MungrouteUserDetailsService userDetailsService;

    @Test
    void loadsOnlyActiveUserByEmail() {
        AppUser user = AppUser.register("active@example.com", "active", "hash", "01012345678");
        when(userPort.findActiveByEmail("active@example.com")).thenReturn(Optional.of(user));

        assertThat(userDetailsService.loadUserByUsername(" ACTIVE@example.com ").getUsername())
                .isEqualTo("active@example.com");
    }

    @Test
    void rejectsDeletedUserById() {
        when(userPort.findActiveById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserById(7L))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
