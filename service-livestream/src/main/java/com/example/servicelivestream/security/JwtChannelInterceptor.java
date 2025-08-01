package com.example.servicelivestream.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // CORRECTION : Gestion similaire au service notification
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.error("❌ Missing or invalid Authorization header in WebSocket connection");
                throw new BadCredentialsException("Missing or invalid Authorization header");
            }

            try {
                String token = authHeader.substring(7);
                Jwt jwt = jwtDecoder.decode(token);

                // CORRECTION : Utiliser JwtAuthenticationToken comme notification service
                JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt);
                accessor.setUser(authToken);

                log.info("✅ WebSocket authenticated for user: {}", jwt.getSubject());

            } catch (JwtException e) {
                log.error("❌ WebSocket authentication failed: {}", e.getMessage());
                throw new BadCredentialsException("Invalid JWT token", e);
            } catch (Exception e) {
                log.error("❌ Unexpected error during WebSocket authentication: {}", e.getMessage());
                throw new BadCredentialsException("Authentication failed", e);
            }
        }

        return message;
    }
}