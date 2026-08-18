package com.mungroute.meet.websocket;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.auth.security.UserPrincipalJwtAuthenticationToken;
import com.mungroute.meet.dto.response.MeetPresenceResponse;
import com.mungroute.meet.service.MeetPresenceService;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
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

class MeetMessageControllerTest {
    @Test
    void delegatesStompPresenceWithAuthenticatedUser() {
        MeetPresenceService service = mock(MeetPresenceService.class);
        MeetMessageController controller = new MeetMessageController(service);
        MungrouteUserPrincipal user = new MungrouteUserPrincipal(7L, "meet@example.com", "password", "meet",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("7")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        var authentication = new UserPrincipalJwtAuthenticationToken(jwt, user);
        var request = new PresenceUpdateRequest(27L, OffsetDateTime.now(), new BigDecimal("126.978"),
                new BigDecimal("37.5665"), new BigDecimal("7"), null, false, 100);
        var expected = new MeetPresenceResponse(27L, OffsetDateTime.now(), 4, List.of(), null);
        when(service.update(7L, request)).thenReturn(expected);

        assertThat(controller.update(request, authentication)).isSameAs(expected);
        verify(service).update(7L, request);
    }
}
