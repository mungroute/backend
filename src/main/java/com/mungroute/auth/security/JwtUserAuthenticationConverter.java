package com.mungroute.auth.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractOAuth2TokenAuthenticationToken<Jwt>> {
    private final MungrouteUserDetailsService userDetailsService;

    public JwtUserAuthenticationConverter(MungrouteUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt jwt) {
        MungrouteUserPrincipal principal = userDetailsService.loadUserById(Long.valueOf(jwt.getSubject()));
        return new UserPrincipalJwtAuthenticationToken(jwt, principal);
    }
}
