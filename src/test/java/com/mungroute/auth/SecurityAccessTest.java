package com.mungroute.auth;

import com.mungroute.auth.config.SecurityConfig;
import com.mungroute.auth.config.SecurityProblemWriter;
import com.mungroute.auth.controller.AuthController;
import com.mungroute.auth.service.AuthService;
import com.mungroute.auth.service.PasswordResetService;
import com.mungroute.auth.security.JwtUserAuthenticationConverter;
import com.mungroute.auth.security.AccessTokenSessionValidator;
import com.mungroute.auth.security.MungrouteUserDetailsService;
import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.user.controller.UserController;
import com.mungroute.place.controller.PlaceController;
import com.mungroute.place.dto.PlaceSearchResponse;
import com.mungroute.place.service.PlaceService;
import com.mungroute.user.dto.response.UserResponse;
import com.mungroute.walk.controller.WalkSessionController;
import com.mungroute.walk.service.WalkRecordService;
import com.mungroute.walk.service.WalkSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({WalkSessionController.class, UserController.class, AuthController.class, PlaceController.class})
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtUserAuthenticationConverter.class})
@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-that-is-at-least-32-bytes-long-123456",
        "security.jwt.issuer=mungroute-test",
        "security.jwt.access-token-ttl=30m",
        "security.jwt.refresh-token-ttl=14d",
        "security.jwt.cookie-secure=false",
        "security.cors.allowed-origin-patterns=http://localhost:*",
        "security.password-reset.verification-ttl=10m",
        "security.password-reset.reset-token-ttl=10m",
        "security.password-reset.expose-code=false"
})
class SecurityAccessTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoBean
    MungrouteUserDetailsService userDetailsService;

    @MockitoBean
    AccessTokenSessionValidator accessTokenSessionValidator;

    @MockitoBean
    WalkSessionService walkSessionService;

    @MockitoBean
    WalkRecordService walkRecordService;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    PasswordResetService passwordResetService;

    @MockitoBean
    PlaceService placeService;

    @Test
    void anonymousUserCannotAccessWalkApi() throws Exception {
        mockMvc.perform(post("/api/walks/1/end"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void anonymousUserCannotAccessUserOrSessionApi() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void crossOriginRequestCannotUseRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Origin", "https://evil.example")
                        .cookie(new jakarta.servlet.http.Cookie("MUNGROUTE_REFRESH", "stolen-cookie")))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointPassesSecurityWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void prometheusEndpointPassesSecurityWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void anonymousUserCanSearchNearbyRestaurants() throws Exception {
        when(placeService.findNearbyRestaurants(37.55, 127.04, 2000, 1, 20))
                .thenReturn(new PlaceSearchResponse(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/places/restaurants/nearby")
                        .param("latitude", "37.55")
                        .param("longitude", "127.04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void anonymousUserCanListSeoulJungGuRestaurants() throws Exception {
        when(placeService.findJungGuRestaurants(37.564, 126.997, 1, 100))
                .thenReturn(new PlaceSearchResponse(List.of(), 1, 100, 0));

        mockMvc.perform(get("/api/places/restaurants/areas/seoul-jung-gu")
                        .param("latitude", "37.564")
                        .param("longitude", "126.997"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void validJwtRestoresUserDetailsPrincipal() throws Exception {
        MungrouteUserPrincipal principal = new MungrouteUserPrincipal(
                1L, "mango@example.com", "{bcrypt}hash", "망고보호자",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(userDetailsService.loadUserById(1L)).thenReturn(principal);
        when(accessTokenSessionValidator.isActive("active-session")).thenReturn(true);
        when(authService.getUser(1L)).thenReturn(new UserResponse(1L, principal.email(), principal.nickname(), "01012345678"));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mungroute-test").subject("1").issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("sid", "active-session").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("mango@example.com"));
    }

    @Test
    void revokedRefreshSessionInvalidatesItsAccessToken() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mungroute-test").subject("1").issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("sid", "revoked-session").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }
}
