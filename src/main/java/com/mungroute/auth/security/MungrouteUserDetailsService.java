package com.mungroute.auth.security;

import com.mungroute.auth.port.AuthUserPort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MungrouteUserDetailsService implements UserDetailsService {
    private final AuthUserPort userPort;

    public MungrouteUserDetailsService(AuthUserPort userPort) {
        this.userPort = userPort;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userPort.findActiveByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(MungrouteUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public MungrouteUserPrincipal loadUserById(Long userId) {
        return userPort.findActiveById(userId)
                .map(MungrouteUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
