package com.mungroute.auth.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.util.Map;

public class UserPrincipalJwtAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {
    private final MungrouteUserPrincipal principal;

    public UserPrincipalJwtAuthenticationToken(Jwt jwt, MungrouteUserPrincipal principal) {
        super(jwt, principal.getAuthorities());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public Map<String, Object> getTokenAttributes() {
        return getToken().getClaims();
    }
}
