package com.mungroute.auth.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractOAuth2TokenAuthenticationToken<Jwt>> {
    private final MungrouteUserDetailsService userDetailsService;
    private final AccessTokenSessionValidator sessionValidator;

    public JwtUserAuthenticationConverter(
            MungrouteUserDetailsService userDetailsService,
            AccessTokenSessionValidator sessionValidator
    ) {
        this.userDetailsService = userDetailsService;
        this.sessionValidator = sessionValidator;
    }

    @Override
    public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt jwt) {
        if (!sessionValidator.isActive(jwt.getClaimAsString("sid"))) {
            throw new BadCredentialsException("Access token session is no longer active");
        }
        MungrouteUserPrincipal principal = userDetailsService.loadUserById(Long.valueOf(jwt.getSubject()));
        return new UserPrincipalJwtAuthenticationToken(jwt, principal);
    }
}
