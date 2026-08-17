package com.mungroute.proximity.websocket;

import com.mungroute.auth.security.JwtUserAuthenticationConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class PresenceStompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtUserAuthenticationConverter authenticationConverter;

    public PresenceStompAuthInterceptor(
            JwtDecoder jwtDecoder,
            JwtUserAuthenticationConverter authenticationConverter
    ) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BadCredentialsException("WebSocket access token is required");
        }

        String rawToken = authorization.substring(7).trim();
        if (rawToken.isEmpty()) {
            throw new BadCredentialsException("WebSocket access token is required");
        }

        Jwt jwt = jwtDecoder.decode(rawToken);
        Authentication authentication = authenticationConverter.convert(jwt);
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("WebSocket access token is invalid");
        }
        accessor.setUser(authentication);
        return message;
    }
}
