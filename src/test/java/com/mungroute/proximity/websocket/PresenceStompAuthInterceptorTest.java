package com.mungroute.proximity.websocket;

import com.mungroute.auth.security.JwtUserAuthenticationConverter;
import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.auth.security.UserPrincipalJwtAuthenticationToken;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceStompAuthInterceptorTest {

    @Mock
    JwtDecoder jwtDecoder;
    @Mock
    JwtUserAuthenticationConverter authenticationConverter;

    @Test
    void authenticatesConnectFrameWithBearerToken() {
        PresenceStompAuthInterceptor interceptor = new PresenceStompAuthInterceptor(
                jwtDecoder, authenticationConverter, new WebSocketMetrics(new SimpleMeterRegistry())
        );
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "HS256")
                .subject("7")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        MungrouteUserPrincipal user = new MungrouteUserPrincipal(
                7L, "walker@example.com", "password", "walker",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UserPrincipalJwtAuthenticationToken authentication =
                new UserPrincipalJwtAuthenticationToken(jwt, user);
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);
        when(authenticationConverter.convert(jwt)).thenReturn(authentication);

        Message<byte[]> message = connectMessage("Bearer valid-token");
        Message<?> result = interceptor.preSend(message, null);
        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isSameAs(authentication);
    }

    @Test
    void rejectsConnectFrameWithoutToken() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PresenceStompAuthInterceptor interceptor = new PresenceStompAuthInterceptor(
                jwtDecoder, authenticationConverter, new WebSocketMetrics(registry));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), null))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(registry.get("mungroute.websocket.errors")
                .tag("type", "authentication").counter().count()).isEqualTo(1);
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
