package com.mungroute.proximity.websocket;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.auth.security.UserPrincipalJwtAuthenticationToken;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.dto.response.PresenceUpdateResponse;
import com.mungroute.proximity.service.PresenceUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceMessageControllerTest {

    @Test
    void delegatesPresenceUpdateUsingAuthenticatedUserId() {
        PresenceUpdateService service = mock(PresenceUpdateService.class);
        PresenceMessageController controller = new PresenceMessageController(service);
        MungrouteUserPrincipal user = new MungrouteUserPrincipal(
                7L, "walker@example.com", "password", "walker",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("7")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        UserPrincipalJwtAuthenticationToken authentication =
                new UserPrincipalJwtAuthenticationToken(jwt, user);
        PresenceUpdateRequest request = new PresenceUpdateRequest(
                27L,
                OffsetDateTime.now(),
                new BigDecimal("126.9780"),
                new BigDecimal("37.5665"),
                new BigDecimal("7.0"),
                new BigDecimal("90.0"),
                false,
                100
        );
        PresenceUpdateResponse expected = new PresenceUpdateResponse(
                27L, OffsetDateTime.now(), 4, List.of()
        );
        when(service.update(7L, request)).thenReturn(expected);

        PresenceUpdateResponse result = controller.update(request, authentication);

        assertThat(result).isSameAs(expected);
        verify(service).update(7L, request);
    }
}
