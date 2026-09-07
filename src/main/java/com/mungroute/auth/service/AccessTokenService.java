package com.mungroute.auth.service;

import com.mungroute.auth.config.JwtProperties;
import com.mungroute.user.domain.AppUser;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AccessTokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public IssuedAccessToken issue(AppUser user, String sessionHash) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUserId().toString())
                .claim("sid", sessionHash)
                .claim("email", user.getEmail())
                .claim("nickname", user.getNickname())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, properties.accessTokenTtl().toSeconds());
    }

    public record IssuedAccessToken(String value, long expiresIn) {
    }
}
