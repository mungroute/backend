package com.mungroute.auth.security;

import com.mungroute.user.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record MungrouteUserPrincipal(
        Long userId,
        String email,
        String password,
        String nickname,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {
    public static MungrouteUserPrincipal from(AppUser user) {
        return new MungrouteUserPrincipal(
                user.getUserId(), user.getEmail(), user.getPasswordHash(), user.getNickname(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
